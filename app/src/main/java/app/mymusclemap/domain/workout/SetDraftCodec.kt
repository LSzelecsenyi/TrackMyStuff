package app.mymusclemap.domain.workout

import org.json.JSONException
import org.json.JSONObject

object SetDraftCodec {
    fun encode(draft: ActualSetDraft): String {
        return JSONObject()
            .put("reps", draft.repsText)
            .put("load", draft.loadKind.name)
            .put("weight", draft.weightText)
            .put("minutes", draft.minutesText)
            .put("seconds", draft.secondsText)
            .put("distance", draft.distanceText)
            .put("unit", draft.distanceUnit.name)
            .toString()
    }

    fun decode(raw: String?): ActualSetDraft? {
        if (raw.isNullOrBlank()) {
            return null
        }
        return try {
            val obj = JSONObject(raw)
            ActualSetDraft(
                repsText = obj.optString("reps"),
                loadKind = PlannedLoadKind.valueOf(obj.getString("load")),
                weightText = obj.optString("weight"),
                minutesText = obj.optString("minutes", "0"),
                secondsText = obj.optString("seconds", "0"),
                distanceText = obj.optString("distance"),
                distanceUnit = DistanceUnit.valueOf(obj.optString("unit", DistanceUnit.METERS.name))
            )
        } catch (_: JSONException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
