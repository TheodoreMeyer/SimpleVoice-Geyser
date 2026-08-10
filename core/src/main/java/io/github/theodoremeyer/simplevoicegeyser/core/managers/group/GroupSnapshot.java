package io.github.theodoremeyer.simplevoicegeyser.core.managers.group;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Full visible-group snapshot for a viewer.
 *
 * @param revision directory revision (group list / counts)
 * @param membershipRevision monotonic membership revision for this viewer session
 * @param currentGroupId authoritative joined group id for the viewer (nullable)
 * @param groups visible groups
 */
public record GroupSnapshot(
        long revision,
        long membershipRevision,
        String currentGroupId,
        List<GroupInfo> groups
) {

    /**
     * Serialize as a {@code groups_snapshot} payload body (without type).
     *
     * @return json
     */
    public JSONObject toJson() {
        JSONArray array = new JSONArray();
        for (GroupInfo info : groups) {
            array.put(info.toJson());
        }
        JSONObject json = new JSONObject()
                .put("revision", revision)
                .put("membershipRevision", membershipRevision)
                .put("groups", array);
        if (currentGroupId != null) {
            json.put("currentGroupId", currentGroupId);
        } else {
            json.put("currentGroupId", JSONObject.NULL);
        }
        return json;
    }
}
