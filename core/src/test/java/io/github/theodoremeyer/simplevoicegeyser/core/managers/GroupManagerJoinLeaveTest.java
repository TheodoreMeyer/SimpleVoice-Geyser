package io.github.theodoremeyer.simplevoicegeyser.core.managers;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.Platform;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgLogger;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.DataType;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.schedule.ControllableTaskScheduler;
import io.github.theodoremeyer.simplevoicegeyser.core.schedule.TaskScheduler;
import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupManagerJoinLeaveTest {

    private ControllableTaskScheduler scheduler;
    private FakeBridge bridge;
    private GroupManager manager;
    private FakePlayer player;

    @BeforeEach
    void setUp() {
        scheduler = new ControllableTaskScheduler();
        new SvgCore(new FakePlatform(scheduler));
        bridge = new FakeBridge();
        manager = new GroupManager(bridge);
        player = new FakePlayer("Alice");
        SvgCore.getPlayerManager().addPlayer(player);
    }

    @AfterEach
    void tearDown() {
        SvgCore.disable();
    }

    @Test
    void joinLeaveAreIdempotentAndBumpRevision() {
        Group group = fakeGroup(UUID.randomUUID(), "Alpha", false);
        manager.addGroup(group);
        bridge.putGroup(group);
        long afterAdd = manager.getRevision();

        bridge.putConnection(player.getUniqueId(), fakeConnection(null));

        GroupManager.OpResult first = manager.joinGroup(player, group.getId(), null);
        assertTrue(first.success());
        long afterJoin = manager.getRevision();
        assertTrue(afterJoin > afterAdd);

        // Connection still reports already in group for idempotent path.
        bridge.putConnection(player.getUniqueId(), fakeConnection(group));
        GroupManager.OpResult second = manager.joinGroup(player, group.getId(), null);
        assertTrue(second.success());
        assertEquals(afterJoin, manager.getRevision(), "idempotent join must not bump revision");

        GroupManager.OpResult leave = manager.leaveGroupDetailed(player);
        assertTrue(leave.success());
        long afterLeave = manager.getRevision();
        assertTrue(afterLeave > afterJoin);

        bridge.putConnection(player.getUniqueId(), fakeConnection(null));
        GroupManager.OpResult leaveAgain = manager.leaveGroupDetailed(player);
        assertTrue(leaveAgain.success());
        assertEquals(afterLeave, manager.getRevision(), "idempotent leave must not bump revision");
    }

    @Test
    void protectedJoinRejectsWrongPasswordFailClosed() {
        UUID id = UUID.randomUUID();
        Group group = fakeGroup(id, "Secret", true);
        manager.addGroup(group);
        bridge.putGroup(group);
        manager.getPasswordStore().put(id, "correct");
        bridge.putConnection(player.getUniqueId(), fakeConnection(null));

        assertFalse(manager.joinGroup(player, id, "wrong").success());
        assertFalse(manager.joinGroup(player, id, "").success());
        assertTrue(manager.joinGroup(player, id, "correct").success());
    }

    @Test
    void hiddenGroupsExcludedFromDirectory() {
        Group visible = fakeGroup(UUID.randomUUID(), "Visible", false);
        Group hidden = fakeGroup(UUID.randomUUID(), "Hidden", false, true);
        manager.addGroup(visible);
        manager.addGroup(hidden);

        assertEquals(List.of("Visible"), manager.getGroupNames());
        assertEquals(1, manager.snapshotVisible(player.getUniqueId()).groups().size());
        assertTrue(manager.findVisibleByName("Hidden").isEmpty());
    }

    @Test
    void revisionIsMonotonicAcrossStateChanges() {
        long r0 = manager.getRevision();
        manager.addGroup(fakeGroup(UUID.randomUUID(), "One", false));
        long r1 = manager.getRevision();
        Group g2 = fakeGroup(UUID.randomUUID(), "Two", false);
        manager.addGroup(g2);
        long r2 = manager.getRevision();
        manager.removeGroup(g2);
        long r3 = manager.getRevision();

        assertTrue(r1 > r0);
        assertTrue(r2 > r1);
        assertTrue(r3 > r2);
    }

    @Test
    void duplicateJoinEventDoesNotDoubleBumpRevision() {
        Group group = fakeGroup(UUID.randomUUID(), "Alpha", false);
        manager.addGroup(group);
        bridge.putConnection(player.getUniqueId(), fakeConnection(null));
        bridge.putGroup(group);

        assertTrue(manager.joinGroup(player, group.getId(), null).success());
        long afterJoin = manager.getRevision();

        // Simulate SVC JoinGroupEvent for the same authoritative state.
        manager.onExternalMembershipEvent(player.getUniqueId(), group.getId(), true);
        assertEquals(afterJoin, manager.getRevision(), "duplicate event must not double-increment revision");
    }

    @Test
    void successfulCreateBecomesVisibleAfterCreatorAssignment() {
        bridge.putConnection(player.getUniqueId(), fakeConnection(null));
        UUID createdId = UUID.randomUUID();
        bridge.setGroupFactory((name, type, persistent) -> fakeGroup(createdId, name, false));

        GroupManager.OpResult result = manager.createGroupDetailed(
                player, "Crew", null, Group.Type.ISOLATED, false, false, false
        );
        assertTrue(result.success());
        assertEquals(Boolean.TRUE, result.joined());
        assertEquals(createdId, result.groupId());
        assertTrue(manager.getGroupNames().contains("Crew"));
        assertEquals(1, manager.snapshotVisible(player.getUniqueId()).groups().size());
        assertTrue(manager.snapshotVisible(player.getUniqueId()).groups().get(0).joined());
    }

    @Test
    void blankOptionalPasswordSucceedsForWebCreate() {
        bridge.putConnection(player.getUniqueId(), fakeConnection(null));
        UUID createdId = UUID.randomUUID();
        bridge.setGroupFactory((name, type, persistent) -> fakeGroup(createdId, name, false));

        GroupManager.OpResult result = manager.createGroupDetailed(
                player, "OpenLobby", "   ", Group.Type.OPEN, false, false, true
        );
        assertTrue(result.success());
        assertTrue(manager.getGroupNames().contains("OpenLobby"));
    }

    @Test
    void webCreateDisabledReturnsClearFailure() {
        bridge.putConnection(player.getUniqueId(), fakeConnection(null));
        SvgCore.getConfig().GROUPS_ALLOW_WEB_CREATION.set(false);

        GroupManager.OpResult result = manager.createGroupDetailed(
                player, "Denied", null, Group.Type.ISOLATED, false, false, true
        );
        assertFalse(result.success());
        assertTrue(result.error().toLowerCase().contains("disabled"));
    }

    @Test
    void invalidBlankNameFails() {
        bridge.putConnection(player.getUniqueId(), fakeConnection(null));
        GroupManager.OpResult result = manager.createGroupDetailed(
                player, "   ", null, Group.Type.ISOLATED, false, false, true
        );
        assertFalse(result.success());
        assertTrue(result.error().toLowerCase().contains("name"));
    }

    @Test
    void leaveUpdatesMembershipAndDirectory() {
        Group group = fakeGroup(UUID.randomUUID(), "Alpha", false);
        manager.addGroup(group);
        bridge.putConnection(player.getUniqueId(), fakeConnection(null));
        bridge.putGroup(group);

        assertTrue(manager.joinGroup(player, group.getId(), null).success());
        assertTrue(manager.snapshotVisible(player.getUniqueId()).groups().get(0).joined());

        GroupManager.OpResult leave = manager.leaveGroupDetailed(player, group.getId());
        assertTrue(leave.success());
        assertEquals(Boolean.TRUE, leave.left());
        assertEquals(group.getId(), leave.previousGroupId());
        assertNull(leave.currentGroupId());
        assertFalse(manager.snapshotVisible(player.getUniqueId()).groups().get(0).joined());

        // Idempotent already-left
        GroupManager.OpResult leaveAgain = manager.leaveGroupDetailed(player, group.getId());
        assertTrue(leaveAgain.success());
        assertEquals(Boolean.TRUE, leaveAgain.left());
    }

    @Test
    void snapshotIncludesCurrentGroupIdAndMembershipRevision() {
        Group group = fakeGroup(UUID.randomUUID(), "Alpha", false);
        manager.addGroup(group);
        bridge.putConnection(player.getUniqueId(), fakeConnection(null));
        bridge.putGroup(group);

        assertTrue(manager.joinGroup(player, group.getId(), null).success());
        var snapshot = manager.snapshotVisible(player.getUniqueId());
        assertEquals(group.getId().toString(), snapshot.currentGroupId());
        assertTrue(snapshot.membershipRevision() > 0);
        assertTrue(snapshot.groups().get(0).joined());

        GroupManager.OpResult leave = manager.leaveGroupDetailed(player, group.getId());
        assertTrue(leave.success());
        var afterLeave = manager.snapshotVisible(player.getUniqueId());
        assertNull(afterLeave.currentGroupId());
        assertFalse(afterLeave.groups().get(0).joined());
    }

    private static Group fakeGroup(UUID id, String name, boolean hasPassword) {
        return fakeGroup(id, name, hasPassword, false);
    }

    private static Group fakeGroup(UUID id, String name, boolean hasPassword, boolean hidden) {
        return (Group) Proxy.newProxyInstance(
                Group.class.getClassLoader(),
                new Class<?>[]{Group.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getName" -> name;
                    case "hasPassword" -> hasPassword;
                    case "isHidden" -> hidden;
                    case "isPersistent" -> false;
                    case "getType" -> Group.Type.OPEN;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> name;
                    default -> null;
                }
        );
    }

    private static VoicechatConnection fakeConnection(Group currentGroup) {
        AtomicReference<Group> groupRef = new AtomicReference<>(currentGroup);
        return (VoicechatConnection) Proxy.newProxyInstance(
                VoicechatConnection.class.getClassLoader(),
                new Class<?>[]{VoicechatConnection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getGroup" -> groupRef.get();
                    case "isInGroup" -> groupRef.get() != null;
                    case "setGroup" -> {
                        groupRef.set(args[0] == null ? null : (Group) args[0]);
                        yield null;
                    }
                    case "isInstalled" -> false;
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

    private static final class FakeBridge extends VoiceChatBridge {
        private final Map<UUID, VoicechatConnection> connections = new LinkedHashMap<>();
        private final Map<UUID, Group> liveGroups = new LinkedHashMap<>();
        private GroupFactory groupFactory = (name, type, persistent) ->
                fakeGroup(UUID.randomUUID(), name, false);

        private final VoicechatServerApi api = (VoicechatServerApi) Proxy.newProxyInstance(
                VoicechatServerApi.class.getClassLoader(),
                new Class<?>[]{VoicechatServerApi.class},
                (proxy, method, args) -> {
                    if ("getConnectionOf".equals(method.getName()) && args != null && args.length == 1) {
                        return connections.get((UUID) args[0]);
                    }
                    if ("getGroups".equals(method.getName())) {
                        return List.copyOf(liveGroups.values());
                    }
                    if ("groupBuilder".equals(method.getName())) {
                        return fakeGroupBuilder();
                    }
                    return defaultValue(method.getReturnType());
                }
        );

        void putConnection(UUID uuid, VoicechatConnection connection) {
            connections.put(uuid, connection);
        }

        void putGroup(Group group) {
            if (group != null && group.getId() != null) {
                liveGroups.put(group.getId(), group);
            }
        }

        void setGroupFactory(GroupFactory factory) {
            this.groupFactory = factory;
        }

        private Group.Builder fakeGroupBuilder() {
            return (Group.Builder) Proxy.newProxyInstance(
                    Group.Builder.class.getClassLoader(),
                    new Class<?>[]{Group.Builder.class},
                    new java.lang.reflect.InvocationHandler() {
                        private String name = "Group";
                        private Group.Type type = Group.Type.NORMAL;
                        private boolean persistent;

                        @Override
                        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
                            return switch (method.getName()) {
                                case "setName" -> {
                                    name = (String) args[0];
                                    yield proxy;
                                }
                                case "setType" -> {
                                    type = (Group.Type) args[0];
                                    yield proxy;
                                }
                                case "setPersistent" -> {
                                    persistent = (Boolean) args[0];
                                    yield proxy;
                                }
                                case "setPassword" -> proxy;
                                case "build" -> {
                                    Group created = groupFactory.create(name, type, persistent);
                                    putGroup(created);
                                    yield created;
                                }
                                default -> defaultValue(method.getReturnType());
                            };
                        }
                    }
            );
        }

        @Override
        public VoicechatServerApi getVcServerApi() {
            return api;
        }
    }

    @FunctionalInterface
    private interface GroupFactory {
        Group create(String name, Group.Type type, boolean persistent);
    }

    private static final class FakePlayer extends SvgPlayer {
        private final UUID uuid = UUID.randomUUID();
        private final String name;

        private FakePlayer(String name) {
            this.name = name;
        }

        @Override public UUID getUniqueId() { return uuid; }
        @Override public boolean hasPermission(String permission) { return true; }
        @Override public void chat(String message) {}
        @Override public boolean isOnline() { return true; }
        @Override public Object getPlayer() { return null; }
        @Override public void sendMessage(String message) {}
        @Override public String getName() { return name; }
    }

    private static final class FakePlatform implements Platform {
        private final SvgFile config = new FakeSvgFile();
        private final TaskScheduler scheduler;
        private final SvgLogger logger = new NoopLogger();

        private FakePlatform(TaskScheduler scheduler) {
            this.scheduler = scheduler;
        }

        @Override public void disable() {}
        @Override public String getPrefix() { return ""; }
        @Override public String getServerMcVersion() { return "test"; }
        @Override public String getServerPlatform() { return "test"; }
        @Override public VoiceChatBridge registerVcBridge() { return null; }
        @Override public SvgLogger getSvgLogger() { return logger; }
        @Override public SvgFile getFile(DataType type) { return config; }
        @Override public File getDataFolder() { return new File("."); }
        @Override public boolean isDependencyEnabled(String name) { return false; }
        @Override public TaskScheduler getTaskScheduler() { return scheduler; }
    }

    private static final class FakeSvgFile extends SvgFile {
        private final Map<String, Object> values = new LinkedHashMap<>(Map.of("updatechecker.enable", false));

        @Override public Set<String> getKeys() { return values.keySet(); }
        @Override public boolean has(String key) { return values.containsKey(key); }
        @Override public void set(String path, Object value) { values.put(path, value); }
        @Override public String getString(String path) { return getString(path, null); }
        @Override public String getString(String path, String def) {
            Object value = values.get(path);
            return value == null ? def : String.valueOf(value);
        }
        @Override public boolean getBoolean(String path, boolean def) {
            Object value = values.get(path);
            return value instanceof Boolean bool ? bool : def;
        }
        @Override public int getInt(String path, int def) {
            Object value = values.get(path);
            return value instanceof Number number ? number.intValue() : def;
        }
        @Override public void save() {}
        @Override public void reload() {}
        @Override public File getFile() { return new File("test-config.yml"); }
        @Override public String backup() { return ""; }
        @Override public double getDouble(String path, double def) {
            Object value = values.get(path);
            return value instanceof Number number ? number.doubleValue() : def;
        }
        @Override
        @SuppressWarnings("unchecked")
        public List<String> getStringList(String path, List<String> def) {
            Object value = values.get(path);
            return value instanceof List<?> list ? (List<String>) list : def;
        }
    }

    private static final class NoopLogger implements SvgLogger {
        @Override public void info(String msg) {}
        @Override public void warning(String msg) {}
        @Override public void error(String msg) {}
        @Override public void severe(String msg) {}
        @Override public void error(String msg, Throwable t) {}
    }
}
