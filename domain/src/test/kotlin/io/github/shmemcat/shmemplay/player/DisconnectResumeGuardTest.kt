package io.github.shmemcat.shmemplay.player

import org.junit.Assert.*
import org.junit.Test
class DisconnectResumeGuardTest {
    @Test fun manualActionPreventsReconnectAutoplay() { val guard=DisconnectResumeGuard();guard.disconnected("q","a");guard.invalidate();assertFalse(guard.consume("q","a",true)) }
    @Test fun queueSwitchPreventsReconnectAutoplay() { val guard=DisconnectResumeGuard();guard.disconnected("q","a");assertFalse(guard.consume("other","a",true)) }
    @Test fun reconnectIsOneShotAndOptIn() { val guard=DisconnectResumeGuard();guard.disconnected("q","a");assertFalse(guard.consume("q","a",false));guard.disconnected("q","a");assertTrue(guard.consume("q","a",true));assertFalse(guard.consume("q","a",true)) }
}
