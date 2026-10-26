package dev.quinton.goa.web.trivia

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.data.TriviaProgressRepository
import dev.quinton.goa.core.data.trivia.HeroOption
import dev.quinton.goa.core.data.trivia.HeroProgressUi
import dev.quinton.goa.core.data.trivia.PlayingAsState
import dev.quinton.goa.core.data.trivia.TriviaChallengeStart
import dev.quinton.goa.core.data.trivia.TriviaHomeAction
import dev.quinton.goa.core.data.trivia.TriviaHomeModel
import dev.quinton.goa.core.data.trivia.TriviaHomeUiState
import dev.quinton.goa.core.data.trivia.TriviaQuizRequest
import dev.quinton.goa.core.data.trivia.TriviaText
import dev.quinton.goa.core.domain.trivia.AnswerStyle
import dev.quinton.goa.core.domain.trivia.CardGroup
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaStat
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.ui.AlertDialog
import dev.quinton.goa.web.ui.BodyText
import dev.quinton.goa.web.ui.BottomSheet
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.Chip
import dev.quinton.goa.web.ui.ChipRow
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.GoaCard
import dev.quinton.goa.web.ui.GuestAvatar
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconButton
import dev.quinton.goa.web.ui.IconLink
import dev.quinton.goa.web.ui.IconSize
import dev.quinton.goa.web.ui.ListRow
import dev.quinton.goa.web.ui.Load
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.PlayerAvatar
import dev.quinton.goa.web.ui.RowList
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.SectionHeader
import dev.quinton.goa.web.ui.publishHeight
import dev.quinton.goa.web.ui.rememberLoad
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.maxLength
import org.jetbrains.compose.web.attributes.onSubmit
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Form
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul

/** The quiz screen for a request of the shared trivia models. */
internal fun TriviaQuizRequest.toRoute() = Route.TriviaQuiz(config, factKeys, challenge)

/** Why the Playing-as picker is open. */
private enum class PlayingAsPicker {
    /** "Playing as ▾": any active player or Guest. */
    CHOOSE,

    /** Challenge tapped while playing as Guest: a named player is needed, then the Challenge opens. */
    CHALLENGE,
}

private sealed interface HomeSheet {
    data object More : HomeSheet

    data object Heroes : HomeSheet

    data class PlayingAs(val mode: PlayingAsPicker) : HomeSheet
}

/**
 * Card trivia (ADR-028, ADR-032): who is playing, overall progress, the quiz setup and progress per
 * hero; Start quiz and the Challenge pinned at the bottom, the leaderboard on the top bar. The
 * shared [TriviaHomeModel] (the phone's too) does the work; [heroId] opens it on one hero (a hero
 * page's "Learn the numbers").
 */
@Composable
fun TriviaHomeScreen(heroId: String?) {
    val services = LocalServices.current
    val content = services.data.content
    val group = rememberTriviaGroup()
    var attempt by remember { mutableStateOf(0) }
    val scope = rememberScreenScope { services.messages.show("Couldn't save that. Try again.") }
    val model = remember(heroId, attempt) {
        TriviaHomeModel(
            scope,
            heroId,
            content.catalog,
            content.settings,
            content.triviaProgress,
            group.players,
            group.groups,
        )
    }
    val state by model.uiState.collectAsState()
    // Whether this browser can reach the group's players (W2's store): else rounds are Guest only.
    val playersReady = rememberLoad(Unit) { group.players.observePlayers().first() }.state !is Load.Failed
    var sheet by remember { mutableStateOf<HomeSheet?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val ready = state as? TriviaHomeUiState.Ready

    fun open(request: TriviaQuizRequest?) {
        request?.let { services.router.navigate(it.toRoute()) }
    }

    Screen("Card trivia", actions = {
        if (ready != null) {
            IconLink("trophy", "Leaderboard", Route.TriviaLeaderboard, extraClass = "collapsible")
            IconButton("more", "More options", onClick = { sheet = HomeSheet.More })
        }
    }) {
        when (val current = state) {
            TriviaHomeUiState.Loading -> LoadingState("Loading trivia")

            TriviaHomeUiState.Unavailable -> EmptyState(
                "trivia",
                "No heroes to quiz",
                "The hero data couldn't be read, so there are no card numbers to learn. Check the connection " +
                    "and try again.",
            ) { GoaButton("Try again", { attempt++ }, ButtonKind.TONAL) }

            is TriviaHomeUiState.Ready -> HomeContent(
                state = current,
                onAction = model::onAction,
                onStart = { open(model.start()) },
                onStartHero = { open(model.startHero(it)) },
                onChooseHeroes = { sheet = HomeSheet.Heroes },
                onChoosePlayer = { sheet = HomeSheet.PlayingAs(PlayingAsPicker.CHOOSE) },
                onChallenge = {
                    // A Challenge goes on the board under a name: Guest picks one first.
                    when (val start = model.startChallenge()) {
                        is TriviaChallengeStart.Open -> open(start.request)
                        TriviaChallengeStart.PickPlayer -> sheet = HomeSheet.PlayingAs(PlayingAsPicker.CHALLENGE)
                        TriviaChallengeStart.NotReady -> Unit
                    }
                },
            )
        }
    }

    if (ready != null) {
        when (val open = sheet) {
            HomeSheet.More -> BottomSheet("More", onDismiss = { sheet = null }) {
                RowList {
                    ListRow("Leaderboard", leadingIcon = "trophy", route = Route.TriviaLeaderboard)
                    if (ready.stored.mastered + ready.stored.learning > 0) {
                        ListRow("Reset progress", leadingIcon = "restart", onClick = {
                            sheet = null
                            confirmReset = true
                        })
                    } else {
                        ListRow("Reset progress", subtitle = "Nothing learned yet", leadingIcon = "restart")
                    }
                }
            }

            HomeSheet.Heroes -> BottomSheet("Heroes to quiz", onDismiss = { sheet = null }) {
                HeroPickerContent(
                    options = ready.heroOptions,
                    selected = ready.setup.heroIds,
                    ownedOnly = ready.ownedOnly,
                    ownedCount = ready.ownedHeroCount,
                    onToggle = { model.onAction(TriviaHomeAction.ToggleHero(it)) },
                    onAll = { model.onAction(TriviaHomeAction.AllHeroes) },
                    onDone = { sheet = null },
                )
            }

            is HomeSheet.PlayingAs -> {
                val challenge = open.mode == PlayingAsPicker.CHALLENGE
                BottomSheet(
                    if (challenge) "Who's taking the Challenge?" else "Who's playing?",
                    onDismiss = { sheet = null },
                ) {
                    PlayingAsContent(
                        state = ready.playingAs,
                        challenge = challenge,
                        playersReady = playersReady,
                        onPick = { id ->
                            sheet = null
                            if (challenge) model.playAsAndChallenge(id, ::open) else model.playAs(id)
                        },
                        onAdd = { name ->
                            sheet = null
                            if (challenge) model.addPlayer(name, ::open) else model.addPlayer(name)
                        },
                    )
                }
            }

            null -> Unit
        }
    }
    if (ready != null && confirmReset) {
        val learned = ready.stored.mastered
        val learning = ready.stored.learning
        AlertDialog("Reset progress?", onDismiss = { confirmReset = false }, buttons = {
            GoaButton("Cancel", { confirmReset = false }, ButtonKind.TEXT)
            GoaButton("Reset", {
                confirmReset = false
                model.resetProgress()
            }, ButtonKind.DANGER)
        }) {
            Text(
                buildString {
                    append("This forgets ")
                    append(TriviaText.count(learned, "learned number"))
                    if (learning > 0) append(" and ${TriviaText.count(learning, "number")} you're still learning")
                    append(". Every card number starts again as new. This can't be undone.")
                },
            )
        }
    }
}

@Composable
private fun HomeContent(
    state: TriviaHomeUiState.Ready,
    onAction: (TriviaHomeAction) -> Unit,
    onStart: () -> Unit,
    onStartHero: (String) -> Unit,
    onChooseHeroes: () -> Unit,
    onChoosePlayer: () -> Unit,
    onChallenge: () -> Unit,
) {
    // A size container: the pinned bar stacks its buttons and the Challenge line moves up into the
    // list when they don't fit side by side (narrow screen or large text), as on Android.
    Div({ classes("goa-trivia-home") }) {
        PlayingAsRow(state.playingAs, onChoosePlayer)
        P({ classes("goa-trivia-challenge-line", "in-list", "t-body-small", "c-variant") }) {
            Text(TriviaText.CHALLENGE_LINE)
        }
        OverallCard(state)
        SectionHeader("New quiz")
        SetupBlock(state, onAction, onChooseHeroes)
        SectionHeader("By hero")
        P({ classes("goa-trivia-caption", "t-label-medium", "c-variant") }) { Text("Tap one to quiz it") }
        Ul({ classes("goa-trivia-heroes") }) {
            state.heroes.forEach { hero -> Li { HeroProgressRow(hero, onClick = { onStartHero(hero.heroId) }) } }
        }
        StartBar(state, onStart, onChallenge)
    }
}

/**
 * "Playing as Sam ▾" with the player's crest; the line under it says whether the rounds go on the
 * leaderboard, and a note when the player isn't this device's "You" (whose learning progress this is).
 */
@Composable
private fun PlayingAsRow(playing: PlayingAsState, onClick: () -> Unit) {
    val player = playing.player
    val name = player?.name ?: "Guest"
    val isMe = player != null && player.id == playing.me?.id
    val subtitle = if (playing.isGuest) "Not on the leaderboard" else "Rounds go on the leaderboard"
    Button({
        type(ButtonType.Button)
        classes("goa-trivia-playing")
        attr("aria-label", "Playing as $name" + (if (isMe) ", you" else "") + ", $subtitle. Change who's playing")
        onClick { onClick() }
    }) {
        PlayingAvatar(player)
        Span({ classes("goa-trivia-playing-text") }) {
            Span({ classes("goa-trivia-playing-name") }) {
                Span({ classes("t-title-medium") }) {
                    Text("Playing as $name")
                    Span({ classes("goa-trivia-caret") }) { Text(" ▾") }
                }
                if (isMe) YouTag()
            }
            Span({ classes("t-body-small", "c-variant") }) { Text(subtitle) }
        }
    }
    TriviaText.playingAsNote(playing)?.let { note ->
        P({ classes("goa-trivia-note", "t-body-small", "c-variant") }) { Text(note) }
    }
}

@Composable
private fun OverallCard(state: TriviaHomeUiState.Ready) {
    val overall = state.overall
    GoaCard {
        Div({ classes("goa-trivia-overall") }) {
            P({ classes("t-headline-small") }) {
                Text("${TriviaText.number(overall.mastered)} of ${TriviaText.count(overall.total, "number")} learned")
            }
            BodyText(
                if (state.ownedOnly) {
                    "Every number on the Action cards of the heroes you own."
                } else {
                    "Every number on every hero's Action cards."
                },
                "t-body-medium",
            )
            MasteryBar(overall)
            MasteryLegend(overall)
            BodyText("A number counts as learned after three right answers in a row.", "t-body-small")
        }
    }
}

@Composable
private fun SetupBlock(
    state: TriviaHomeUiState.Ready,
    onAction: (TriviaHomeAction) -> Unit,
    onChooseHeroes: () -> Unit,
) {
    val setup = state.setup
    Div({ classes("goa-trivia-setup") }) {
        SetupGroup("Heroes") { HeroScopeRow(state, onChooseHeroes) }
        SetupGroup("Numbers") {
            ChipRow(wrap = true) {
                TriviaStat.entries.forEach { stat ->
                    Chip(TriviaText.stat(stat), stat in setup.stats, { onAction(TriviaHomeAction.ToggleStat(stat)) })
                }
            }
        }
        SetupGroup("Cards") {
            ChipRow(wrap = true) {
                CardGroup.entries.forEach { group ->
                    Chip(
                        TriviaText.group(group),
                        group in setup.groups,
                        { onAction(TriviaHomeAction.ToggleGroup(group)) },
                    )
                }
            }
        }
        SetupGroup("Answer with") {
            ChipRow(wrap = true) {
                Chip("4 choices", setup.answerStyle == AnswerStyle.CHOICES, {
                    onAction(TriviaHomeAction.SetAnswerStyle(AnswerStyle.CHOICES))
                })
                Chip("Recall", setup.answerStyle == AnswerStyle.RECALL, {
                    onAction(TriviaHomeAction.SetAnswerStyle(AnswerStyle.RECALL))
                })
            }
            BodyText(
                if (setup.answerStyle == AnswerStyle.CHOICES) {
                    "Pick the number from four close ones."
                } else {
                    "Pick from every number the stat has on any card: no hints."
                },
                "t-body-small",
            )
        }
        SetupGroup("Questions") {
            ChipRow(wrap = true) {
                TriviaConfig.LENGTHS.forEach { length ->
                    Chip(TriviaText.length(length), setup.length == length, {
                        onAction(TriviaHomeAction.SetLength(length))
                    })
                }
            }
        }
        DuelsRow(state, onAction)
    }
}

@Composable
private fun SetupGroup(label: String, content: @Composable () -> Unit) {
    Div({ classes("goa-trivia-setup-group") }) {
        H3({ classes("t-title-small") }) { Text(label) }
        content()
    }
}

@Composable
private fun HeroScopeRow(state: TriviaHomeUiState.Ready, onChooseHeroes: () -> Unit) {
    val names = state.selectedHeroNames
    val summary = when {
        names.isEmpty() -> if (state.ownedOnly) "All heroes you own" else "All heroes"
        names.size <= 2 -> names.joinToString(", ")
        else -> "${names.take(2).joinToString(", ")} +${names.size - 2}"
    }
    // All heroes = the owned ones; the options also list a chosen hero outside them.
    val count = if (names.isEmpty()) state.ownedHeroCount else names.size
    Button({
        type(ButtonType.Button)
        classes("goa-trivia-scope")
        attr("aria-label", "Heroes: $summary, ${TriviaText.count(count, "hero", "heroes")}. Choose")
        onClick { onChooseHeroes() }
    }) {
        Span({ classes("goa-trivia-scope-text") }) {
            Span({ classes("t-body-large") }) { Text(summary) }
            Span({ classes("t-body-small", "c-variant") }) { Text(TriviaText.count(count, "hero", "heroes")) }
        }
        Span({ classes("t-label-large", "c-primary") }) { Text("Choose") }
    }
}

@Composable
private fun DuelsRow(state: TriviaHomeUiState.Ready, onAction: (TriviaHomeAction) -> Unit) {
    val enabled = state.initiativeSelected
    val checked = enabled && state.setup.duels
    Button({
        type(ButtonType.Button)
        classes("goa-trivia-switch-row")
        attr("role", "switch")
        attr("aria-checked", checked.toString())
        if (!enabled) disabled()
        onClick { onAction(TriviaHomeAction.SetDuels(!checked)) }
    }) {
        Span({ classes("goa-trivia-switch-text") }) {
            Span({ classes("t-title-small") }) { Text("Initiative duels") }
            Span({ classes("t-body-small", "c-variant") }) {
                Text(
                    if (enabled) {
                        "Now and then: which of two cards has the higher initiative?"
                    } else {
                        "Needs Initiative under Numbers."
                    },
                )
            }
        }
        Span({
            classes("goa-trivia-switch")
            attr("aria-hidden", "true")
        })
    }
}

/**
 * The bar pinned under the list: the live "N numbers in this quiz · M learned" line (or why Start is
 * off), Challenge and Start quiz with the Challenge's one-line explanation. Text and buttons side by
 * side on a wide window (landscape); the two buttons stack at large text.
 */
@Composable
private fun StartBar(state: TriviaHomeUiState.Ready, onStart: () -> Unit, onChallenge: () -> Unit) {
    val selection = state.selection
    val note = TriviaText.startNote(state)
    Div({
        classes("goa-trivia-startbar")
        publishHeight("--pinned-footer")
    }) {
        Div({ classes("goa-trivia-startbar-text") }) {
            P({
                classes("t-numeric", "c-variant")
                attr("aria-live", "polite")
            }) {
                Text(
                    "${TriviaText.count(selection.total, "number")} in this quiz · " +
                        "${TriviaText.number(selection.mastered)} learned",
                )
            }
            if (note != null) {
                P({ classes("t-body-medium", if (state.canStart) "c-variant" else "c-error") }) { Text(note) }
            }
            P({ classes("goa-trivia-challenge-line", "in-bar", "t-body-small", "c-variant") }) {
                Text(TriviaText.CHALLENGE_LINE)
            }
        }
        Div({ classes("goa-trivia-startbar-buttons") }) {
            GoaButton("Challenge", onChallenge, ButtonKind.OUTLINED, icon = "bolt")
            GoaButton("Start quiz", onStart, icon = "play", enabled = state.canStart)
        }
    }
}

@Composable
private fun HeroProgressRow(hero: HeroProgressUi, onClick: () -> Unit) {
    val p = hero.progress
    val canQuiz = hero.quizNumbers > 0
    val spoken = buildString {
        append("Quiz ${hero.name}")
        append(TriviaText.spokenUnverified(hero.verified))
        append(", ${p.mastered} of ${TriviaText.count(p.total, "number")} learned")
        if (p.learning > 0) append(", ${p.learning} learning")
        if (!canQuiz) append(". No numbers with these settings")
    }
    Button({
        type(ButtonType.Button)
        classes("goa-trivia-hero-row")
        attr("aria-label", spoken)
        if (!canQuiz) disabled()
        onClick { onClick() }
    }) {
        HeroMedallion(hero.portrait)
        Span({ classes("goa-trivia-hero-body") }) {
            Span({ classes("goa-trivia-hero-head") }) {
                Span({ classes("goa-trivia-hero-name", "t-title-medium") }) { Text(hero.name) }
                if (!hero.verified) UnverifiedBadge()
                Span({ classes("t-numeric", "c-variant") }) { Text("${p.mastered}/${p.total}") }
            }
            MasteryBar(p, thin = true)
            if (!canQuiz) {
                Span({ classes("t-body-small", "c-variant") }) { Text("No numbers with these settings") }
            }
        }
        if (canQuiz) Icon("chevron_right", IconSize.SMALL)
    }
}

/**
 * Who plays the next rounds: active players (this device's "You" first), Guest (not for the
 * Challenge) and an "Add" field, never a dead end when the group is empty.
 */
@Composable
private fun PlayingAsContent(
    state: PlayingAsState,
    challenge: Boolean,
    playersReady: Boolean,
    onPick: (String) -> Unit,
    onAdd: (String) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    BodyText(
        if (challenge) {
            "Challenge scores go on the leaderboard under a player's name."
        } else {
            "Rounds played as a player go on the leaderboard. Guest rounds aren't recorded."
        },
        "t-body-small",
    )
    if (!playersReady) {
        P({ classes("t-body-medium", "c-error") }) {
            Text("This browser can't read the group's players right now, so rounds are played as Guest.")
        }
    }
    Div({
        classes("goa-trivia-picks")
        attr("role", "radiogroup")
        attr("aria-label", "Playing as")
    }) {
        state.players.forEach { player ->
            PickRow(
                selected = !challenge && player.id == state.player?.id,
                title = player.name,
                subtitle = null,
                isMe = player.id == state.me?.id,
                onClick = { onPick(player.id) },
            ) { PlayerAvatar(player) }
        }
        if (!challenge) {
            PickRow(
                selected = state.isGuest,
                title = "Guest",
                subtitle = "Not on the leaderboard",
                isMe = false,
                onClick = { onPick(TriviaProgressRepository.GUEST) },
            ) { GuestAvatar() }
        }
    }
    if (playersReady) {
        Form(attrs = {
            classes("goa-trivia-add")
            onSubmit { event ->
                event.preventDefault()
                if (newName.isNotBlank()) {
                    onAdd(newName)
                    newName = ""
                }
            }
        }) {
            Label(forId = "trivia-add-player", attrs = { classes("t-label-large") }) {
                Text(if (state.players.isEmpty()) "Name of the first player" else "Someone missing? Add them")
            }
            Div({ classes("goa-trivia-add-row") }) {
                Input(InputType.Text) {
                    id("trivia-add-player")
                    classes("goa-trivia-input")
                    value(newName)
                    maxLength(TriviaHomeModel.MAX_NAME_LENGTH)
                    placeholder("Name")
                    attr("autocapitalize", "words")
                    attr("autocomplete", "off")
                    attr("enterkeyhint", "done")
                    onInput { newName = it.value.take(TriviaHomeModel.MAX_NAME_LENGTH) }
                }
                Button({
                    type(ButtonType.Submit)
                    classes("goa-button", "tonal")
                    if (newName.isBlank()) disabled()
                }) {
                    Icon("person_add", IconSize.SMALL)
                    Text("Add")
                }
            }
        }
    }
}

@Composable
private fun PickRow(
    selected: Boolean,
    title: String,
    subtitle: String?,
    isMe: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Button({
        type(ButtonType.Button)
        classes("goa-trivia-pick")
        attr("role", "radio")
        attr("aria-checked", selected.toString())
        attr("aria-label", listOfNotNull(title, "you".takeIf { isMe }, subtitle).joinToString(", "))
        onClick { onClick() }
    }) {
        leading()
        Span({ classes("goa-trivia-pick-text") }) {
            Span({ classes("goa-trivia-pick-title") }) {
                Span({ classes("t-title-medium") }) { Text(title) }
                if (isMe) YouTag()
            }
            subtitle?.let { Span({ classes("t-body-small", "c-variant") }) { Text(it) } }
        }
        Span({
            classes("goa-trivia-radio")
            attr("aria-hidden", "true")
        })
    }
}

/**
 * Pick the heroes to quiz: all of them, or any you choose; changes apply as you tap. [options] are
 * the owned heroes plus any chosen hero outside them, so "All heroes" counts [ownedCount].
 */
@Composable
private fun HeroPickerContent(
    options: List<HeroOption>,
    selected: List<String>,
    ownedOnly: Boolean,
    ownedCount: Int,
    onToggle: (String) -> Unit,
    onAll: () -> Unit,
    onDone: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()
    val shown = options.filter { option ->
        trimmed.isEmpty() ||
            option.name.contains(trimmed, ignoreCase = true) ||
            option.title?.contains(trimmed, ignoreCase = true) == true
    }
    Div({ classes("goa-trivia-picker-head") }) {
        P({ classes("t-body-small", "c-variant") }) {
            Text(
                if (selected.isEmpty()) "All heroes" else TriviaText.count(selected.size, "hero", "heroes") + " chosen",
            )
        }
        GoaButton("Done", onDone, ButtonKind.TEXT)
    }
    Input(InputType.Search) {
        classes("goa-trivia-input", "search")
        value(query)
        placeholder("Search heroes")
        attr("aria-label", "Search heroes")
        attr("enterkeyhint", "search")
        onInput { query = it.value }
    }
    Div({ classes("goa-trivia-picks") }) {
        if (trimmed.isEmpty()) {
            CheckRow(
                checked = selected.isEmpty(),
                title = "All heroes",
                subtitle = if (ownedOnly) {
                    "The ${TriviaText.count(ownedCount, "hero", "heroes")} you own (Settings)"
                } else {
                    "Every hero, $ownedCount in all"
                },
                verified = true,
                onClick = { if (selected.isNotEmpty()) onAll() },
            ) {
                Span({ classes("goa-trivia-all-icon") }) { Icon("nav_heroes") }
            }
        }
        shown.forEach { option ->
            CheckRow(
                checked = option.heroId in selected,
                title = option.name,
                subtitle = option.title,
                verified = option.verified,
                onClick = { onToggle(option.heroId) },
            ) { HeroMedallion(option.portrait) }
        }
        if (shown.isEmpty()) {
            BodyText("No hero matches “$trimmed”.", "t-body-medium")
        }
    }
}

@Composable
private fun CheckRow(
    checked: Boolean,
    title: String,
    subtitle: String?,
    verified: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Button({
        type(ButtonType.Button)
        classes("goa-trivia-pick")
        attr("role", "checkbox")
        attr("aria-checked", checked.toString())
        onClick { onClick() }
    }) {
        leading()
        Span({ classes("goa-trivia-pick-text") }) {
            Span({ classes("goa-trivia-pick-title") }) {
                Span({ classes("t-title-medium") }) { Text(title) }
                if (!verified) UnverifiedBadge()
            }
            subtitle?.let { Span({ classes("t-body-small", "c-variant") }) { Text(it) } }
        }
        Span({
            classes("goa-trivia-check")
            attr("aria-hidden", "true")
        }) { if (checked) Icon("check", IconSize.SMALL) }
    }
}
