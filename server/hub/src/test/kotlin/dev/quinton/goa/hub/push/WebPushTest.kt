package dev.quinton.goa.hub.push

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.hub.FakeClock
import dev.quinton.goa.hub.store.DeviceStore
import dev.quinton.goa.hub.store.Devices
import java.security.Signature
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WebPushTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun b64(text: String): ByteArray = Base64.getUrlDecoder().decode(text.replace(" ", "").replace("\n", ""))

    // RFC 8291 §5 and Appendix A.
    private val asPublic =
        b64("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8")
    private val asPrivate = b64("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw")
    private val uaPublic =
        b64("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4")
    private val uaPrivate = b64("q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94")
    private val authSecret = b64("BTBZMqHH6r4Tts7J_aSIgg")
    private val salt = b64("DGv6ra1nlYgDCS1FRnbzlw")
    private val plaintext = "When I grow up, I want to be a watermelon".toByteArray()
    private val expected = b64(
        "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27ml" +
            "mlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPT" +
            "pK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN",
    )

    @Test
    fun `encryption matches the RFC 8291 worked example byte for byte`() {
        val body = WebPushCrypto.encrypt(plaintext, uaPublic, authSecret, P256.keyPair(asPublic, asPrivate), salt)
        assertThat(body).isEqualTo(expected)
    }

    @Test
    fun `a receiver holding its private key can decrypt a fresh message`() {
        val body = WebPushCrypto.encrypt("hello".toByteArray(), uaPublic, authSecret)
        assertThat(decrypt(body)).isEqualTo("hello".toByteArray())
        // Fresh salt and sender key every time.
        assertThat(WebPushCrypto.encrypt("hello".toByteArray(), uaPublic, authSecret)).isNotEqualTo(body)
    }

    /** The receiver's side of RFC 8291, written independently from the spec. */
    private fun decrypt(body: ByteArray): ByteArray {
        val salt = body.copyOfRange(0, 16)
        val idLength = body[20].toInt()
        val senderPublic = body.copyOfRange(21, 21 + idLength)
        val ciphertext = body.copyOfRange(21 + idLength, body.size)
        val secret = KeyAgreement.getInstance("ECDH").run {
            init(P256.decodePrivate(uaPrivate))
            doPhase(P256.decodePublic(senderPublic), true)
            generateSecret()
        }
        fun hmac(key: ByteArray, data: ByteArray) = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }
        val ikm = hmac(hmac(authSecret, secret), "WebPush: info".toByteArray() + 0 + uaPublic + senderPublic + 1)
        val prk = hmac(salt, ikm)
        val cek = hmac(prk, "Content-Encoding: aes128gcm".toByteArray() + 0 + 1).copyOf(16)
        val nonce = hmac(prk, "Content-Encoding: nonce".toByteArray() + 0 + 1).copyOf(12)
        val plain = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(cek, "AES"), GCMParameterSpec(128, nonce))
            doFinal(ciphertext)
        }
        assertThat(plain.last()).isEqualTo(2.toByte())
        return plain.copyOf(plain.size - 1)
    }

    private operator fun ByteArray.plus(value: Int): ByteArray = this + value.toByte()

    @Test
    fun `the VAPID token is an ES256 JWT for the push service's origin`() {
        val vapid = Vapid.loadOrCreate(tmp.root.toPath().resolve("vapid.json")) { "https://goa.example.org" }
        val header = vapid.authorization("https://web.push.apple.com/QGd4…/abc", nowSeconds = 1_000)
        val token = header.substringAfter("vapid t=").substringBefore(",")
        val key = header.substringAfter("k=")
        assertThat(key).isEqualTo(vapid.publicKey)
        val (h, c, s) = token.split(".")
        val claims = Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(c))).jsonObject
        assertThat(claims["aud"]!!.jsonPrimitive.content).isEqualTo("https://web.push.apple.com")
        assertThat(claims["sub"]!!.jsonPrimitive.content).isEqualTo("https://goa.example.org")
        assertThat(claims["exp"]!!.jsonPrimitive.long).isEqualTo(1_000 + Vapid.DEFAULT_TTL_SECONDS)
        val verified = Signature.getInstance("SHA256withECDSAinP1363Format").run {
            initVerify(P256.decodePublic(Vapid.unb64(key)))
            update("$h.$c".toByteArray())
            verify(Base64.getUrlDecoder().decode(s))
        }
        assertThat(verified).isTrue()
        // The same keys come back after a restart.
        assertThat(
            Vapid.loadOrCreate(tmp.root.toPath().resolve("vapid.json")) {
                "x"
            }.publicKey,
        ).isEqualTo(vapid.publicKey)
    }

    private class Recorded(val endpoint: String, val headers: Map<String, String>, val body: ByteArray)

    private fun service(
        clock: FakeClock,
        sent: MutableList<Recorded>,
        status: (String) -> Int = {
            201
        },
    ): Pair<PushService, String> {
        val root = tmp.root.toPath().resolve("devices")
        val device = Devices(root).create("iPhone", clock.nowMillis()).first
        val vapid = Vapid.loadOrCreate(tmp.root.toPath().resolve("vapid.json")) { "https://goa.example.org" }
        val transport = PushTransport { endpoint, headers, body ->
            sent += Recorded(endpoint, headers, body)
            status(endpoint)
        }
        return PushService(DeviceStore(root), vapid, clock, transport) to device.id
    }

    private val subscription = PushSubscription(
        endpoint = "https://web.push.apple.com/QGd4abc",
        p256dh = Base64.getUrlEncoder().withoutPadding().encodeToString(uaPublic),
        auth = Base64.getUrlEncoder().withoutPadding().encodeToString(authSecret),
    )

    @Test
    fun `send encrypts for each subscription and drops the ones the service says are gone`() = runBlocking {
        val sent = CopyOnWriteArrayList<Recorded>()
        val (push, device) = service(FakeClock(), sent) { if ("gone" in it) 410 else 201 }
        push.subscribe(device, subscription)
        push.subscribe(device, subscription.copy(endpoint = "https://fcm.googleapis.com/fcm/send/gone"))
        val result = push.send(device, PushPayload("Time's up", "Turn 2", url = "/timer", tag = "timer"))
        assertThat(result).isEqualTo(PushResult(sent = 1, removed = 1, failed = 0))
        assertThat(push.subscriptions(device).map { it.endpoint }).containsExactly(subscription.endpoint)
        val apple = sent.first { "apple" in it.endpoint }
        assertThat(apple.headers["Content-Encoding"]).isEqualTo("aes128gcm")
        assertThat(apple.headers["TTL"]).isEqualTo("120")
        assertThat(apple.headers["Authorization"]).startsWith("vapid t=")
        val payload = Json.parseToJsonElement(decrypt(apple.body).decodeToString()).jsonObject
        assertThat(payload["title"]!!.jsonPrimitive.content).isEqualTo("Time's up")
        assertThat(payload["url"]!!.jsonPrimitive.content).isEqualTo("/timer")
    }

    @Test
    fun `only browser push services and well-formed keys are accepted`() = runBlocking {
        val (push, device) = service(FakeClock(), CopyOnWriteArrayList())
        for (endpoint in listOf(
            "http://web.push.apple.com/x",
            "https://evil.example/x",
            "https://push.apple.com.evil.example/x",
            "https://u@fcm.googleapis.com/x",
            // A push service's own name on another port: the hub would knock on whatever listens there.
            "https://web.push.apple.com:22/x",
            "https://fcm.googleapis.com:8080/x",
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { push.subscribe(device, subscription.copy(endpoint = endpoint)) }
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { push.subscribe(device, subscription.copy(auth = "AAAA")) }
        }
        assertThat(push.subscriptions(device)).isEmpty()
        assertThat(PushService.isPushService("https://web.push.apple.com:443/x")).isTrue()
    }

    @Test
    fun `a scheduled push is sent at its time, survives a restart, and can be cancelled`() = runBlocking {
        val clock = FakeClock()
        val sent = CopyOnWriteArrayList<Recorded>()
        val (push, device) = service(clock, sent)
        push.subscribe(device, subscription)
        push.schedule(device, clock.nowMillis() + 200, PushPayload("Phase over"), key = "timer")
        push.schedule(device, clock.nowMillis() + 60_000, PushPayload("Later"), key = "other")
        push.cancel(device, "other")
        assertThat(push.scheduled(device).map { it.key }).containsExactly("timer")
        withTimeout(5_000) { while (sent.isEmpty()) delay(20) }
        assertThat(push.scheduled(device)).isEmpty()
        push.close()

        // A schedule stored before a restart is re-armed by start().
        val (push2, _) = PushService(DeviceStore(tmp.root.toPath().resolve("devices")), push.vapid, clock, { _, _, _ ->
            201
        }) to
            device
        push2.schedule(device, clock.nowMillis() + 50, PushPayload("Again"), key = "timer")
        push2.close()
        val sent3 = CopyOnWriteArrayList<Recorded>()
        val push3 =
            PushService(DeviceStore(tmp.root.toPath().resolve("devices")), push.vapid, clock, { e, h, b ->
                sent3 +=
                    Recorded(e, h, b)
                201
            })
        push3.start()
        withTimeout(5_000) { while (sent3.isEmpty()) delay(20) }
        push3.close()
    }
}
