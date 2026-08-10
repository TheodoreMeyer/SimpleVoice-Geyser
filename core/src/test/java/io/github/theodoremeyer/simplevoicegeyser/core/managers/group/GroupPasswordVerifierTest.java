package io.github.theodoremeyer.simplevoicegeyser.core.managers.group;

import de.maxhenkel.voicechat.api.Group;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupPasswordVerifierTest {

    @Test
    void noPasswordAcceptsNullOrBlank() {
        GroupPasswordVerifier verifier = new GroupPasswordVerifier(new GroupPasswordStore());
        Group group = fakeGroup(UUID.randomUUID(), false);

        assertEquals(GroupPasswordVerifier.Result.VALID, verifier.verify(group, null));
        assertEquals(GroupPasswordVerifier.Result.VALID, verifier.verify(group, ""));
        assertEquals(GroupPasswordVerifier.Result.VALID, verifier.verify(group, "   "));
    }

    @Test
    void hasPasswordRejectsBlank() {
        GroupPasswordVerifier verifier = new GroupPasswordVerifier(new GroupPasswordStore());
        Group group = fakeGroup(UUID.randomUUID(), true);

        assertEquals(GroupPasswordVerifier.Result.INVALID, verifier.verify(group, null));
        assertEquals(GroupPasswordVerifier.Result.INVALID, verifier.verify(group, ""));
        assertEquals(GroupPasswordVerifier.Result.INVALID, verifier.verify(group, "  "));
    }

    @Test
    void svgManagedHashValidatesWithoutExposingPassword() {
        GroupPasswordStore store = new GroupPasswordStore();
        UUID id = UUID.randomUUID();
        store.put(id, "secret-pass");

        GroupPasswordVerifier verifier = new GroupPasswordVerifier(store);
        Group group = fakeGroup(id, true);

        assertEquals(GroupPasswordVerifier.Result.VALID, verifier.verify(group, "secret-pass"));
        assertEquals(GroupPasswordVerifier.Result.INVALID, verifier.verify(group, "wrong"));
        assertTrue(store.has(id));
        assertTrue(store.verify(id, "secret-pass"));
        assertFalse(store.verify(id, "wrong"));
    }

    @Test
    void unknownStructureFailsClosedAsUnavailable() {
        GroupPasswordVerifier verifier = new GroupPasswordVerifier(new GroupPasswordStore());
        // Proxy Group has no SVC "group" field → reflection fails closed.
        Group group = fakeGroup(UUID.randomUUID(), true);

        GroupPasswordVerifier.Result result = verifier.verify(group, "anything");
        assertEquals(GroupPasswordVerifier.Result.UNAVAILABLE, result);
        assertFalse(result == GroupPasswordVerifier.Result.VALID);
    }

    private static Group fakeGroup(UUID id, boolean hasPassword) {
        return (Group) Proxy.newProxyInstance(
                Group.class.getClassLoader(),
                new Class<?>[]{Group.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getName" -> "test";
                    case "hasPassword" -> hasPassword;
                    case "isHidden" -> false;
                    case "isPersistent" -> false;
                    case "getType" -> Group.Type.NORMAL;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> "FakeGroup(" + id + ")";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class || type == long.class) {
            return 0;
        }
        return null;
    }
}
