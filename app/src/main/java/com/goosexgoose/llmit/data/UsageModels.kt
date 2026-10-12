package com.goosexgoose.llmit.data

import org.json.JSONObject

data class Window(
    val utilization: Float,
    val resetsAtEpochMs: Long,
)

data class UsageSnapshot(
    val fiveHour: Window,
    val sevenDay: Window,
    val fableWeekly: Window?,
    val fetchedAtEpochMs: Long,
    val planTier: String? = null,
    val billingCycleEndMs: Long = 0L,
    val resetCredits: Int = -1,
) {
    companion object {
        fun parse(body: String, now: Long): UsageSnapshot {
            val root = JSONObject(body)
            return UsageSnapshot(
                fiveHour = root.getJSONObject("five_hour").toWindow(),
                sevenDay = root.getJSONObject("seven_day").toWindow(),
                fableWeekly = root.optJSONArray("limits")?.findFableWeekly(),
                fetchedAtEpochMs = now,
            )
        }

        private fun JSONObject.toWindow(): Window {
            val util = optDouble("utilization", 0.0).toFloat()
            val reset = optString("resets_at", "")
            return Window(util, Iso8601.toEpochMs(reset))
        }

        private fun org.json.JSONArray.findFableWeekly(): Window? {
            for (i in 0 until length()) {
                val entry = optJSONObject(i) ?: continue
                val modelName = entry.optJSONObject("scope")
                    ?.optJSONObject("model")
                    ?.optString("display_name", "")
                    .orEmpty()
                if (modelName.contains("Fable", ignoreCase = true)) {
                    val util = entry.optDouble("percent", 0.0).toFloat()
                    val reset = entry.optString("resets_at", "")
                    return Window(util, Iso8601.toEpochMs(reset))
                }
            }
            return null
        }

        fun parseOpenAi(body: String, now: Long): UsageSnapshot {
            val root = JSONObject(body)
            val rl = root.optJSONObject("rate_limit") ?: root
            // Pro Lite sends only a weekly primary_window, so slot windows by length, not position.
            val secondary = rl.optJSONObject("secondary_window")
            val windows = listOfNotNull(rl.optJSONObject("primary_window"), secondary)
            val (long, short) = windows.partition {
                val len = it.optLong("limit_window_seconds", 0L)
                if (len > 0L) len >= 86_400L else it === secondary
            }
            val tier = root.optString("plan_type", "").ifBlank { null }
            return UsageSnapshot(
                fiveHour = short.firstOrNull()?.toOpenAiWindow() ?: Window(-1f, 0L),
                sevenDay = long.firstOrNull()?.toOpenAiWindow() ?: Window(-1f, 0L),
                fableWeekly = null,
                fetchedAtEpochMs = now,
                planTier = tier,
                resetCredits = root.optJSONObject("rate_limit_reset_credits")?.optInt("available_count", -1) ?: -1,
            )
        }

        private fun JSONObject.toOpenAiWindow(): Window {
            val pct = optDouble("used_percent", 0.0).toFloat()
            // An unused window hasn't started: reset_at is always now + window and drifts on every
            // poll. Report no reset time rather than a date that keeps moving.
            val notStarted = pct <= 0f &&
                optLong("reset_after_seconds", 0L) >= optLong("limit_window_seconds", Long.MAX_VALUE) - 60
            val resetEpoch = if (notStarted) 0L else optLong("reset_at", 0L) * 1000
            return Window(utilization = pct, resetsAtEpochMs = resetEpoch)
        }

        fun parseCursor(body: String, now: Long): UsageSnapshot {
            val root = JSONObject(body)
            val usage = root.optJSONObject("planUsage") ?: root
            val totalPct = usage.optDouble("totalPercentUsed", 0.0).toFloat()
            val autoPct = usage.optDouble("autoPercentUsed", -1.0).toFloat()
            val apiPct = usage.optDouble("apiPercentUsed", -1.0).toFloat()
            val cycleEnd = root.optLong("billingCycleEnd", 0L)
            val tier = root.optString("planType", "").ifBlank {
                root.optString("plan_type", "")
            }.ifBlank { null }
            return UsageSnapshot(
                fiveHour = Window(utilization = autoPct.takeIf { it >= 0f } ?: totalPct, resetsAtEpochMs = cycleEnd),
                sevenDay = Window(utilization = apiPct.takeIf { it >= 0f } ?: totalPct, resetsAtEpochMs = cycleEnd),
                fableWeekly = null,
                fetchedAtEpochMs = now,
                planTier = tier,
                billingCycleEndMs = cycleEnd,
            )
        }
    }
}
