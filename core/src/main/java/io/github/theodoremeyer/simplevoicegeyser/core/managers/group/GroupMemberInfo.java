package io.github.theodoremeyer.simplevoicegeyser.core.managers.group;

import org.json.JSONObject;

/**
 * Username-only group member entry for a viewer snapshot.
 *
 * @param name display name
 * @param you whether this member is the viewing connection
 */
public record GroupMemberInfo(String name, boolean you) {

    /**
     * @return json object
     */
    public JSONObject toJson() {
        return new JSONObject()
                .put("name", name)
                .put("you", you);
    }
}
