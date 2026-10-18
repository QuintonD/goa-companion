package dev.quinton.goa.feature.players.share

import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.feature.players.common.formatDelta
import dev.quinton.goa.feature.players.common.formatPercent
import dev.quinton.goa.feature.players.common.formatRating
import dev.quinton.goa.feature.players.common.label
import dev.quinton.goa.feature.players.common.plural
import dev.quinton.goa.feature.players.common.winLoss
import dev.quinton.goa.feature.players.match.MatchRowUi
import dev.quinton.goa.feature.players.match.SeatUi
import dev.quinton.goa.feature.players.meta.HeroMetaRow
import dev.quinton.goa.feature.players.meta.MetaUiState
import dev.quinton.goa.feature.players.meta.PairRow
import dev.quinton.goa.feature.players.players.LeaderboardRow
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Renders a [StatsPage] as one self-contained HTML document (ADR-027): inline CSS, no scripts,
 * fonts, images or links outside the page, light and dark via `prefers-color-scheme`, printable.
 * Pure Kotlin. Every string that comes from data goes through [escape]; only the fixed markup
 * here is written raw.
 */
object StatsPageRenderer {
    const val APP_NAME = "Guards of Atlantis Companion"

    /** Escapes text for HTML element content and quoted attribute values. */
    fun escape(text: String): String = buildString(text.length + 16) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }

    fun title(page: StatsPage): String = "Guards of Atlantis stats · ${page.generatedIsoDate}"

    fun render(page: StatsPage): String = buildString(INITIAL_CAPACITY) {
        raw("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
        raw("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        raw("<meta name=\"color-scheme\" content=\"light dark\">\n")
        raw("<meta name=\"generator\" content=\"").text("$APP_NAME ${page.appVersion}").raw("\">\n")
        raw("<title>").text(title(page)).raw("</title>\n<style>").raw(CSS).raw("</style>\n</head>\n<body>\n")
        masthead(page)
        raw("<main>\n")
        leaderboard(page)
        playerHeroes(page)
        heroMeta(page.meta)
        group(page.meta)
        matchLog(page)
        raw("</main>\n")
        footer(page)
        raw("</body>\n</html>\n")
    }

    private fun StringBuilder.raw(html: String): StringBuilder = append(html)

    private fun StringBuilder.text(value: String): StringBuilder = append(escape(value))

    private fun StringBuilder.masthead(page: StatsPage) {
        raw("<header class=\"masthead\">").raw(BRAND_MARK).raw("<div>")
        raw("<p class=\"overline\">Guards of Atlantis II</p><h1>Our group stats</h1>")
        raw("<p class=\"muted\">").text(page.generatedOn).raw(" · ")
            .text(plural(page.meta.totalMatches, "rated match", "rated matches")).raw("</p>")
        raw("</div></header>\n")
        raw("<nav aria-label=\"Sections\"><a href=\"#leaderboard\">Leaderboard</a>")
        raw("<a href=\"#best-heroes\">Best heroes</a><a href=\"#heroes\">Hero meta</a>")
        raw("<a href=\"#group\">Group</a><a href=\"#matches\">Matches</a></nav>\n")
    }

    // Leaderboard ---------------------------------------------------------------------------

    private fun StringBuilder.leaderboard(page: StatsPage) {
        val board = page.leaderboard
        section("leaderboard", "Leaderboard")
        if (board.ranked.isEmpty()) {
            empty("No rated matches yet. Ratings appear once a completed match with a winner is logged.")
        } else {
            raw("<div class=\"scroll\"><table>\n<thead><tr>")
            raw("<th scope=\"col\" class=\"n\">#</th><th scope=\"col\">Player</th>")
            raw("<th scope=\"col\" class=\"n\">Elo</th>")
            raw("<th scope=\"col\" class=\"n\">Games</th><th scope=\"col\" class=\"n\">W–L</th>")
            raw("<th scope=\"col\" class=\"n\">Recent</th></tr></thead>\n<tbody>\n")
            board.ranked.forEach { leaderboardRow(it) }
            raw("</tbody></table></div>\n")
            raw("<p class=\"note\">Elo per player, seat-based and double-pilot aware. Provisional: fewer than ")
            raw("10 rated games. Recent: the rating change over the last 5 (or fewer) games.</p>\n")
        }
        if (board.unrated.isNotEmpty()) {
            raw("<p><strong>No games yet:</strong> ").text(board.unrated.joinToString(", ") { it.player.name })
            raw("</p>\n")
        }
        raw("</section>\n")
    }

    private fun StringBuilder.leaderboardRow(row: LeaderboardRow) {
        val rating = row.rating
        raw("<tr><td class=\"n rank\">").text(row.rank?.toString() ?: "–").raw("</td>")
        raw("<th scope=\"row\">").text(row.player.name)
        if (rating.provisional) raw(" <span class=\"tag\">Provisional</span>")
        raw("</th><td class=\"n strong\">").text(formatRating(rating.value)).raw("</td>")
        raw("<td class=\"n\">").text(rating.games.toString()).raw("</td>")
        raw("<td class=\"n\">").text(winLoss(rating.wins, rating.games)).raw("</td><td class=\"n\">")
        val trend = row.trend
        if (trend == null) {
            raw("—")
        } else {
            delta(trend).raw(" <span class=\"muted\">last ").text(row.trendGames.toString()).raw("</span>")
        }
        raw("</td></tr>\n")
    }

    private fun StringBuilder.playerHeroes(page: StatsPage) {
        if (page.playerHeroes.isEmpty()) return
        section("best-heroes", "Best heroes")
        raw("<div class=\"cards\">\n")
        page.playerHeroes.forEach { p ->
            raw("<div class=\"card\"><h3>").text(p.name).raw(" <span class=\"tag\">")
                .raw(if (p.best) "Best heroes" else "Most played").raw("</span></h3><ul class=\"plain\">")
            p.heroes.forEach { h ->
                raw("<li><span>").text(h.name).raw("</span> <span class=\"num muted\">")
                    .text("${winLoss(h.record.wins, h.record.games)} · ${formatPercent(h.record.winRate)}")
                    .raw("</span></li>")
            }
            raw("</ul></div>\n")
        }
        raw("</div>\n<p class=\"note\">Best heroes: 3 or more games and a winning record, ranked like the hero ")
        raw("meta. Without one, the most played heroes.</p>\n</section>\n")
    }

    // Hero meta -----------------------------------------------------------------------------

    private fun StringBuilder.heroMeta(meta: MetaUiState) {
        section("heroes", "Hero meta")
        if (meta.ranked.isEmpty() && meta.unranked.isEmpty()) {
            empty(
                "The meta is still forming. Win, pick and ban rates appear once completed matches and drafts " +
                    "are logged.",
            )
            raw("</section>\n")
            return
        }
        statRow(
            listOf(
                meta.totalMatches.toString() to "Rated matches",
                meta.draftsWithBans.toString() to "Drafts with bans",
                (if (meta.largerTeam.games == 0) "—" else formatPercent(meta.largerTeam.winRate)) to
                    ("Larger team won" + if (meta.largerTeam.games > 0) " (${meta.largerTeam.games})" else ""),
            ),
        )
        if (meta.ranked.isNotEmpty()) {
            raw("<h3>Ranked</h3>\n")
            heroTable(meta.ranked, ranked = true)
        }
        if (meta.unranked.isNotEmpty()) {
            raw("<h3>Not enough games yet</h3>\n")
            heroTable(meta.unranked, ranked = false)
        }
        raw("<p class=\"note\">Heroes and pairs are ranked by the 95 % Wilson lower bound of their win rate, so ")
        raw("small samples don't top the table. Ranking needs ").text(plural(meta.minSample, "game")).raw(".</p>\n")
        raw("</section>\n")
    }

    private fun StringBuilder.heroTable(rows: List<HeroMetaRow>, ranked: Boolean) {
        raw("<div class=\"scroll\"><table").raw(if (ranked) "" else " class=\"dim\"").raw(">\n<thead><tr>")
        if (ranked) raw("<th scope=\"col\" class=\"n\">#</th>")
        raw("<th scope=\"col\">Hero</th><th scope=\"col\" class=\"n\">W–L</th><th scope=\"col\" class=\"n\">Win</th>")
        raw("<th scope=\"col\" class=\"n\">Pick</th><th scope=\"col\" class=\"n\">Ban</th>")
        raw("<th scope=\"col\" class=\"n\">Hero Elo</th><th scope=\"col\">Best pilot</th></tr></thead>\n<tbody>\n")
        rows.forEachIndexed { i, row ->
            val m = row.meta
            raw("<tr>")
            if (ranked) raw("<td class=\"n rank\">").text("${i + 1}").raw("</td>")
            raw("<th scope=\"row\">").text(row.name).raw("</th>")
            cell(winLoss(m.record.wins, m.record.games))
            raw("<td class=\"n strong\">").text(formatPercent(m.winRate)).raw("</td>")
            cell(formatPercent(m.pickRate))
            cell(m.banRate?.let { formatPercent(it) } ?: "—")
            cell(row.rating?.let { formatRating(it.value) } ?: "—")
            raw("<td class=\"nw\">")
            val best = row.bestPlayer
            val record = row.bestRecord
            if (best != null && record != null) {
                text(
                    best.name,
                ).raw(" <span class=\"num muted\">").text(winLoss(record.wins, record.games)).raw("</span>")
            } else {
                raw("—")
            }
            raw("</td></tr>\n")
        }
        raw("</tbody></table></div>\n")
    }

    // Group stats ---------------------------------------------------------------------------

    private fun StringBuilder.group(meta: MetaUiState) {
        val hasGames = meta.winConditions.total > 0
        val parts = listOf(meta.night != null, hasGames, meta.rivals.isNotEmpty(), meta.duos.isNotEmpty())
        if (parts.none { it } && meta.synergies.isEmpty()) return
        section("group", "Group")
        meta.night?.let { night ->
            val nightDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
            raw("<h3>Last game night · ").text(night.date.format(nightDate)).raw("</h3>\n<div class=\"card\">")
            raw("<p class=\"line\"><span>").text(plural(night.matches, "match", "matches")).raw("</span>")
            raw("<span class=\"num\"><span class=\"red\">◆ Red ").text(night.redWins.toString()).raw("</span>")
            raw(" · <span class=\"blue\">● Blue ").text(night.blueWins.toString()).raw("</span></span></p>\n")
            // Same selection as the Meta tab's game-night card: top 3 gains, the biggest loss.
            val up = night.movers.filter { it.delta.roundToInt() > 0 }.take(TOP_MOVERS)
            val down = night.movers.filter { it.delta.roundToInt() < 0 }.reversed().take(1)
            if (up.isNotEmpty() || down.isNotEmpty()) {
                raw("<p class=\"label\">Biggest movers</p><ul class=\"plain\">")
                (up + down).forEach { raw("<li><span>").text(it.name).raw("</span> ").delta(it.delta).raw("</li>") }
                raw("</ul>\n")
            }
            if (meta.streaks.isNotEmpty()) {
                raw("<p class=\"label\">Streaks</p><p>")
                meta.streaks.forEachIndexed { i, s ->
                    if (i > 0) raw(" · ")
                    raw(if (s.won) "<span class=\"up\">" else "<span class=\"down\">")
                        .text("${s.name} ${if (s.won) "W" else "L"}${s.length}").raw("</span>")
                }
                raw("</p>\n<p class=\"note\">W3: three wins in a row up to their latest match.</p>\n")
            }
            raw("</div>\n")
        }
        if (hasGames) gamesEnd(meta)
        if (meta.rivals.isNotEmpty()) pairs("Rivalries", meta.rivals, versus = true)
        if (meta.duos.isNotEmpty()) pairs("Best teammates", meta.duos, versus = false)
        if (meta.synergies.isNotEmpty()) {
            raw("<h3>Strongest hero pairs</h3>\n<div class=\"scroll\"><table>\n<thead><tr><th scope=\"col\">Pair</th>")
            raw("<th scope=\"col\" class=\"n\">W–L</th><th scope=\"col\" class=\"n\">Win</th>")
            raw("</tr></thead>\n<tbody>\n")
            meta.synergies.forEach {
                raw("<tr><th scope=\"row\">").text("${it.heroA} + ${it.heroB}").raw("</th>")
                cell(winLoss(it.record.wins, it.record.games))
                cell(formatPercent(it.record.winRate))
                raw("</tr>\n")
            }
            raw("</tbody></table></div>\n")
        }
        raw("<p class=\"note\">Pairs need ").text(plural(meta.minSample, "game")).raw(" together to be listed.</p>\n")
        raw("</section>\n")
    }

    private fun StringBuilder.gamesEnd(meta: MetaUiState) {
        val mix = meta.winConditions
        raw("<h3>How games end</h3>\n<div class=\"scroll\"><table>\n<thead><tr><th scope=\"col\">Win condition</th>")
        raw("<th scope=\"col\" class=\"n\">Matches</th><th scope=\"col\" class=\"n\">Share</th>")
        raw("</tr></thead>\n<tbody>\n")
        (WinCondition.entries + listOf(null)).forEach { condition ->
            val count = mix.counts[condition] ?: 0
            if (count == 0 && condition == null) return@forEach
            raw("<tr><th scope=\"row\">").text(condition?.label() ?: "Not recorded").raw("</th>")
            cell(count.toString())
            cell(formatPercent(mix.share(condition)))
            raw("</tr>\n")
        }
        raw("</tbody></table></div>\n")
        val red = meta.red
        raw("<div class=\"card\"><p class=\"line\"><span>Sides</span><span class=\"num\"><span class=\"red\">◆ Red ")
            .text("${red.wins} (${formatPercent(red.winRate)})").raw("</span> · <span class=\"blue\">● Blue ")
            .text(red.losses.toString()).raw("</span></span></p>\n")
        GameMode.entries.forEach { mode ->
            meta.durationByMode[mode]?.let { d ->
                raw("<p class=\"line\"><span>").text("Average ${mode.label()}").raw("</span><span class=\"num muted\">")
                    .text("${d.meanMinutes.roundToInt()} min · ${plural(d.matches, "match", "matches")}")
                    .raw("</span></p>\n")
            }
        }
        raw("</div>\n")
    }

    private fun StringBuilder.pairs(title: String, rows: List<PairRow>, versus: Boolean) {
        raw("<h3>").text(title).raw("</h3>\n<div class=\"scroll\"><table>\n<thead><tr><th scope=\"col\">Players</th>")
        raw("<th scope=\"col\" class=\"n\">W–L</th><th scope=\"col\" class=\"n\">Win</th></tr></thead>\n<tbody>\n")
        rows.forEach { p ->
            raw("<tr><th scope=\"row\">").text("${p.aName} ${if (versus) "vs" else "+"} ${p.bName}").raw("</th>")
            cell(winLoss(p.record.wins, p.record.games))
            cell(formatPercent(p.record.winRate))
            raw("</tr>\n")
        }
        raw("</tbody></table></div>\n")
    }

    // Match log -----------------------------------------------------------------------------

    private fun StringBuilder.matchLog(page: StatsPage) {
        section("matches", "Recent matches")
        if (page.matches.isEmpty()) {
            empty("No matches logged yet.")
            raw("</section>\n")
            return
        }
        raw("<p class=\"note\">")
        if (page.totalMatches > page.matches.size) {
            text("The newest ${page.matches.size} of ${page.totalMatches} matches, newest first. ")
            text("Older matches still count in every rating and statistic above.")
        } else {
            text("${plural(page.totalMatches, "match", "matches")}, newest first.")
        }
        raw("</p>\n<ol class=\"matches\">\n")
        page.matches.forEach { match(it) }
        raw("</ol>\n</section>\n")
    }

    private fun StringBuilder.match(item: StatsMatchUi) {
        val m = item.row
        raw("<li class=\"card match\"><p class=\"line\">")
        result(m)
        raw("<span class=\"muted\">").text("${item.day} · ${m.time}").raw("</span></p>\n")
        val summary = listOfNotNull(
            m.mode.label(),
            m.winCondition?.label(),
            m.durationMin?.let { "$it min" },
            // Like the match log: older matches stored the mode as the map; don't repeat it.
            m.map?.takeIf { it.isNotBlank() && !it.equals(m.mode.label(), ignoreCase = true) },
        ).joinToString(" · ")
        raw("<p class=\"muted\">").text(summary).raw("</p>\n<div class=\"teams\">")
        team(Team.RED, m.red, m.winner)
        team(Team.BLUE, m.blue, m.winner)
        raw("</div>\n")
        if (m.notes.isNotBlank()) raw("<p class=\"notes\">“").text(m.notes).raw("”</p>\n")
        raw("</li>\n")
    }

    private fun StringBuilder.result(m: MatchRowUi) {
        val winner = m.winner
        when {
            m.status == MatchStatus.COMPLETED && winner != null ->
                raw(if (winner == Team.RED) "<strong class=\"red\">◆ " else "<strong class=\"blue\">● ")
                    .text("${winner.label()} won").raw("</strong>")

            m.status == MatchStatus.ABANDONED -> raw("<strong class=\"muted\">Abandoned</strong>")

            m.status == MatchStatus.COMPLETED -> raw("<strong class=\"down\">No winner</strong>")

            else -> raw("<strong class=\"accent\">Pending</strong>")
        }
    }

    private fun StringBuilder.team(team: Team, seats: List<SeatUi>, winner: Team?) {
        val css = if (team == Team.RED) "red" else "blue"
        raw("<div class=\"team\"><p class=\"tname ").raw(css).raw("\">")
            .raw(if (team == Team.RED) "◆ " else "● ").text(team.label())
        if (team == winner) raw(" <span class=\"tag\">Winner</span>")
        raw("</p><ul class=\"plain\">")
        // One line per hero seat: a double pilot appears once on each of their seats.
        seats.forEach { seat ->
            raw("<li><span class=\"pilot\">").text(seat.playerName).raw("</span> <span class=\"muted\">")
                .text(seat.heroName).raw("</span>")
            seat.delta?.let { raw(" ").delta(it) }
            if (seat.handicap) raw(" <span class=\"tag\">Handicap</span>")
            if (seat.doublePilot) raw(" <span class=\"tag\">Double pilot</span>")
            raw("</li>")
        }
        raw("</ul></div>")
    }

    // Pieces --------------------------------------------------------------------------------

    private fun StringBuilder.footer(page: StatsPage) {
        raw("<footer><p>Generated ").text(page.generatedOn).raw(" by ").text("$APP_NAME ${page.appVersion}")
        raw(", ratings as of the last logged match")
        page.ratedThrough?.let { raw(" (").text(it).raw(")") }
        raw(".</p>\n<p>Private stats for our play group, recomputed from the match log. Guards of Atlantis II ")
        raw("is a game by Wolff Designa; this page is not made or endorsed by them.</p></footer>\n")
    }

    private fun StringBuilder.section(id: String, title: String) {
        raw("<section id=\"").raw(id).raw("\" aria-labelledby=\"h-").raw(id).raw("\">\n<h2 id=\"h-").raw(id)
            .raw("\">").text(title).raw("</h2>\n")
    }

    private fun StringBuilder.empty(message: String) {
        raw("<p class=\"empty\">").text(message).raw("</p>\n")
    }

    private fun StringBuilder.cell(value: String) {
        raw("<td class=\"n\">").text(value).raw("</td>")
    }

    private fun StringBuilder.delta(value: Double): StringBuilder {
        val rounded = value.roundToInt()
        val css = when {
            rounded > 0 -> "up"
            rounded < 0 -> "down"
            else -> "muted"
        }
        return raw("<span class=\"num ").raw(css).raw("\">").text(formatDelta(value)).raw("</span>")
    }

    private fun StringBuilder.statRow(stats: List<Pair<String, String>>) {
        raw("<dl class=\"stats\">")
        stats.forEach { (value, label) ->
            raw("<div><dt>").text(label).raw("</dt><dd>").text(value).raw("</dd></div>")
        }
        raw("</dl>\n")
    }

    private const val TOP_MOVERS = 3
    private const val INITIAL_CAPACITY = 64 * 1024

    /** Our brand mark (design/icons/brand_mark.svg), inline; decorative. */
    private const val BRAND_MARK =
        "<svg class=\"mark\" viewBox=\"0 0 24 24\" width=\"48\" height=\"48\" aria-hidden=\"true\" " +
            "focusable=\"false\"><defs><linearGradient id=\"goa-bronze\" gradientUnits=\"userSpaceOnUse\" " +
            "x1=\"12\" y1=\"1.5\" x2=\"12\" y2=\"22.5\"><stop offset=\"0\" stop-color=\"#EBCB86\"/>" +
            "<stop offset=\"0.55\" stop-color=\"#C9A35B\"/><stop offset=\"1\" stop-color=\"#8E6C2E\"/>" +
            "</linearGradient></defs><path d=\"M12 1.5L20.5 4.3V11.3c0 5.3-3.5 8.6-8.5 10.9C7 19.9 3.5 16.6 " +
            "3.5 11.3V4.3z\" fill=\"url(#goa-bronze)\"/><path d=\"M12 6.4v13.6M8.1 8v2c0 1.9 1.7 3 3.9 3s3.9-1.1 " +
            "3.9-3V8M9.6 18.5h4.8\" fill=\"none\" stroke=\"#0B1622\" stroke-width=\"1.35\" " +
            "stroke-linecap=\"round\" stroke-linejoin=\"round\"/><path d=\"M12 4.2l1.2 2.3h-2.4zM8.1 5.9l1.1 " +
            "2.1H7zM15.9 5.9l1.1 2.1h-2.2z\" fill=\"#0B1622\" stroke=\"#0B1622\" stroke-width=\"0.6\" " +
            "stroke-linejoin=\"round\"/></svg>"

    /**
     * DESIGN.md tokens: light by default (and for print), dark on `prefers-color-scheme: dark`.
     * Type follows the app's scale (display = Cinzel if installed, body = Inter if installed,
     * else the platform's own fonts; no font files are fetched or embedded).
     */
    private val CSS = """
:root{--bg:#F4EFE6;--surface:#FFFFFF;--high:#EDE5D6;--on:#1A1F26;--muted:#4A5563;--primary:#84662A;
--secondary:#1B786B;--red:#B83A30;--blue:#2C63B0;--error:#B3261E;--border:rgba(132,102,42,.3);
--display:Cinzel,"Trajan Pro",Georgia,"Times New Roman",serif;
--body:Inter,system-ui,-apple-system,"Segoe UI",Roboto,"Helvetica Neue",Arial,sans-serif}
@media screen and (prefers-color-scheme:dark){:root{--bg:#0B1622;--surface:#122334;--high:#1A2E44;
--on:#E8E2D6;--muted:#A9B4C2;--primary:#C9A35B;--secondary:#3FC1B0;--red:#E26459;--blue:#4F8FE0;
--error:#FF6B6B;--border:rgba(201,163,91,.3)}}
*{box-sizing:border-box}
html{-webkit-text-size-adjust:100%;text-size-adjust:100%}
body{margin:0 auto;max-width:56rem;padding:16px;background:var(--bg);color:var(--on);
font:400 16px/1.5 var(--body);letter-spacing:.01em}
h1,h2,h3{font-family:var(--display);font-weight:600;color:var(--on);margin:0}
h1{font-size:28px;line-height:36px}
h2{font-size:22px;line-height:28px;letter-spacing:.03em;margin:32px 0 12px;padding-bottom:6px;
border-bottom:1px solid var(--border)}
h3{font-size:16px;line-height:24px;margin:20px 0 8px;letter-spacing:.02em}
.card h3{margin-top:0}
p{margin:4px 0}
.overline{font:600 12px/16px var(--display);letter-spacing:.12em;text-transform:uppercase;color:var(--primary)}
.masthead{display:flex;gap:16px;align-items:center;padding:8px 0 4px}
.mark{flex:none}
nav{display:flex;flex-wrap:wrap;gap:4px 16px;margin:12px 0 0;font-size:14px}
nav a{color:var(--secondary);text-decoration:none;padding:8px 0}
nav a:focus,nav a:hover{text-decoration:underline}
.muted{color:var(--muted)}
.note{color:var(--muted);font-size:13px;line-height:18px;margin:8px 0}
.empty{background:var(--surface);border:1px solid var(--border);border-radius:12px;padding:16px;color:var(--muted)}
.label{font-size:12px;font-weight:600;letter-spacing:.05em;text-transform:uppercase;color:var(--muted);margin-top:12px}
.num,td.n,th.n{font-variant-numeric:tabular-nums lining-nums;white-space:nowrap}
.strong{font-weight:600}
.rank{color:var(--primary);font-weight:700}
.red{color:var(--red)}
.blue{color:var(--blue)}
.up{color:var(--secondary)}
.down{color:var(--error)}
.accent{color:var(--primary)}
.tag{display:inline-block;font:500 11px/16px var(--body);letter-spacing:.04em;padding:0 6px;
border:1px solid var(--border);border-radius:10px;color:var(--muted);vertical-align:1px;white-space:nowrap}
.scroll{overflow-x:auto;background:var(--surface);border:1px solid var(--border);border-radius:12px}
table{border-collapse:collapse;width:100%;font-size:14px;line-height:20px}
th,td{padding:10px 8px;text-align:left;vertical-align:top}
thead th{font-size:12px;font-weight:500;color:var(--muted);border-bottom:1px solid var(--border)}
tbody tr+tr th,tbody tr+tr td{border-top:1px solid var(--border)}
tbody th{font-weight:500;min-width:8rem}
.nw{white-space:nowrap}
th:first-child,td:first-child{padding-left:12px}
th:last-child,td:last-child{padding-right:12px}
.n{text-align:right}
table.dim tbody th,table.dim tbody td{color:var(--muted)}
.cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(15rem,1fr));gap:12px}
.card{background:var(--surface);border:1px solid var(--border);border-radius:12px;padding:12px 16px;margin:0 0 12px}
.cards .card{margin:0}
.line{display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap}
ul.plain{list-style:none;margin:4px 0;padding:0}
ul.plain li{padding:3px 0}
.stats{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin:0 0 12px}
.stats div{background:var(--surface);border:1px solid var(--border);border-radius:12px;padding:8px 12px}
.stats dt{font-size:12px;color:var(--muted)}
.stats dd{margin:0;font:600 22px/28px var(--body);font-variant-numeric:tabular-nums}
ol.matches{list-style:none;margin:0;padding:0}
.teams{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-top:8px}
.tname{font-weight:600}
.pilot{font-weight:500}
.notes{color:var(--muted);font-size:14px;font-style:italic;margin-top:8px}
footer{margin:40px 0 8px;padding-top:12px;border-top:1px solid var(--border);color:var(--muted);font-size:13px}
@media (max-width:30rem){.teams{grid-template-columns:1fr}.stats dd{font-size:18px}}
@media print{body{max-width:none;padding:0;background:#fff;font-size:12px}nav{display:none}
.scroll{overflow:visible}.card,tr,.stats div{break-inside:avoid}h2,h3{break-after:avoid}
.scroll,.card,.stats div,.empty{background:#fff}}
    """.trimIndent().replace("\n", "")
}
