package io.github.theodoremeyer.simplevoicegeyser.core.managers.group;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Immutable JSON-friendly group directory entry for a viewer.
 *
 * @param uuid group id
 * @param name display name
 * @param type NORMAL / OPEN / ISOLATED
 * @param hasPassword whether a password is required
 * @param passwordProtected alias of hasPassword for newer clients
 * @param persistent whether the group is persistent
 * @param memberCount tracked members
 * @param joined whether the viewer is currently in this group
 * @param members sorted member usernames for the viewer
 */
public record GroupInfo(
        String uuid,
        String name,
        String type,
        boolean hasPassword,
        boolean passwordProtected,
        boolean persistent,
        int memberCount,
        boolean joined,
        List<GroupMemberInfo> members
) {

    /**
     * Serialize for websocket payloads.
     *
     * @return json object
     */
    public JSONObject toJson() {
        JSONArray memberArray = new JSONArray();
        if (members != null) {
            for (GroupMemberInfo member : members) {
                memberArray.put(member.toJson());
            }
        }
        return new JSONObject()
                .put("uuid", uuid)
                .put("name", name)
                .put("type", type)
                .put("hasPassword", hasPassword)
                .put("passwordProtected", passwordProtected)
                .put("persistent", persistent)
                .put("memberCount", memberCount)
                .put("joined", joined)
                .put("members", memberArray);
    }
}
