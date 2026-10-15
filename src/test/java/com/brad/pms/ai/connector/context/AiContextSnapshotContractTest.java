package com.brad.pms.ai.connector.context;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiContextSnapshotContractTest {

    @Test
    void authorizationSnapshotMemoizesTheSameReferenceWithinOneRead() {
        AiContextReadPolicy policy = mock(AiContextReadPolicy.class);
        AiContextReference reference = new AiContextReference("topic", 90L);
        when(policy.canReadReference("topic", 90L)).thenReturn(true);

        ReadAuthorizationSnapshot snapshot = ReadAuthorizationSnapshot.forUser(
                11L, List.of(reference), policy);

        assertThat(snapshot.canRead(reference)).isTrue();
        assertThat(snapshot.canRead(reference)).isTrue();
        verify(policy, times(1)).canReadReference("topic", 90L);
    }

    @Test
    void authorizationSnapshotFailsClosedForAnUnknownReference() {
        AiContextReadPolicy policy = mock(AiContextReadPolicy.class);
        AiContextReference known = new AiContextReference("topic", 90L);
        AiContextReference unknown = new AiContextReference("story", 91L);
        when(policy.canReadReference("topic", 90L)).thenReturn(true);

        ReadAuthorizationSnapshot snapshot = ReadAuthorizationSnapshot.forUser(
                11L, List.of(known), policy);

        assertThat(snapshot.canRead(known)).isTrue();
        assertThat(snapshot.canRead(unknown)).isFalse();
    }
}
