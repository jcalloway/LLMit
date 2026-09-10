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
            val primary = rl.optJSONObject("primary_window")
            val secondary = rl.optJSONObject("secondary_window")
            val tier = root.optString("plan_type", "").ifBlank { null }
            return UsageSnapshot(
                fiveHour = primary?.toOpenAiWindow() ?: Window(-1f, 0L),
                sevenDay = secondary?.toOpenAiWindow() ?: Window(-1f, 0L),
                fableWeekly = null,
                fetchedAtEpochMs = now,
                planTier = tier,
            )
        }

        private fun JSONObject.toOpenAiWindow(): Window {
            val pct = optDouble("used_percent", 0.0).toFloat()
            val resetEpoch = optLong("reset_at", 0L) * 1000
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
