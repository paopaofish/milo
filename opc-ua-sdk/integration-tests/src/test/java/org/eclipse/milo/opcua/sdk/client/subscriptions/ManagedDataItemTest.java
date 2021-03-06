/*
 * Copyright (c) 2019 the Eclipse Milo Authors
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.milo.opcua.sdk.client.subscriptions;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.milo.opcua.sdk.client.subscriptions.BatchModifyMonitoredItems.ModifyMonitoredItemResult;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ManagedDataItemTest extends AbstractManagedItemTest {

    @Override
    protected ManagedDataItem createManagedItem() throws UaException {
        return subscription.createDataItem(Identifiers.Server_ServerStatus_State);
    }

    @Test
    public void getStatusCode() throws UaException {
        ManagedDataItem dataItem1 = subscription.createDataItem(Identifiers.Server_ServerStatus_CurrentTime);
        assertTrue(dataItem1.getStatusCode().isGood());

        ManagedDataItem dataItem2 = subscription.createDataItem(NodeId.parse("ns=2;s=FooBarDoesNotExist"));
        assertEquals(StatusCodes.Bad_NodeIdUnknown, dataItem2.getStatusCode().getValue());
    }

    @Test
    public void samplingInterval() throws Exception {
        ManagedDataItem dataItem1 = createManagedItem();
        assertEquals(subscription.getDefaultSamplingInterval(), dataItem1.getSamplingInterval());
        assertEquals(subscription.getDefaultSamplingInterval(), dataItem1.getMonitoredItem().getRequestedSamplingInterval());
        assertEquals(subscription.getDefaultSamplingInterval(), dataItem1.getMonitoredItem().getRevisedSamplingInterval());

        assertEquals(5000.0, dataItem1.setSamplingInterval(5000.0));
        assertEquals(5000.0, dataItem1.getMonitoredItem().getRequestedSamplingInterval());
        assertEquals(5000.0, dataItem1.getMonitoredItem().getRevisedSamplingInterval());
    }

    @Test
    public void samplingIntervalBatch() throws Exception {
        subscription.createDataItems(Collections.nCopies(10, Identifiers.Server_ServerStatus_State));

        subscription.getDataItems().forEach(
            item -> {
                assertEquals(1000.0, item.getSamplingInterval());
                assertEquals(1000.0, item.getMonitoredItem().getRequestedSamplingInterval());
                assertEquals(1000.0, item.getMonitoredItem().getRevisedSamplingInterval());
            }
        );

        {
            BatchModifyMonitoredItems batch = new BatchModifyMonitoredItems(subscription);

            List<CompletableFuture<Double>> futures = subscription.getDataItems().stream()
                .map(
                    item ->
                        item.setSamplingIntervalAsync(5000.0, batch)
                )
                .collect(Collectors.toList());

            List<ModifyMonitoredItemResult> results = batch.execute();
            results.forEach(r -> assertTrue(r.isOperationResultGood()));

            futures.forEach(f -> {
                assertTrue(f.isDone() && !f.isCompletedExceptionally());
                f.thenAccept(v -> assertEquals(5000.0, v));
            });

            subscription.getDataItems().forEach(
                item -> {
                    assertEquals(5000.0, item.getSamplingInterval());
                    assertEquals(5000.0, item.getMonitoredItem().getRequestedSamplingInterval());
                    assertEquals(5000.0, item.getMonitoredItem().getRevisedSamplingInterval());
                }
            );

            assertEquals(1, batch.getServiceInvocationCount());
        }

        {
            BatchModifyMonitoredItems batch = new BatchModifyMonitoredItems(
                subscription.getClient(),
                subscription.getSubscription()
            );

            subscription.getDataItems().forEach(
                item ->
                    item.setSamplingIntervalAsync(1000.0, batch)
            );
            batch.execute();

            subscription.getDataItems().forEach(
                item -> {
                    assertEquals(1000.0, item.getSamplingInterval());
                    assertEquals(1000.0, item.getMonitoredItem().getRequestedSamplingInterval());
                    assertEquals(1000.0, item.getMonitoredItem().getRevisedSamplingInterval());
                }
            );

            assertEquals(1, batch.getServiceInvocationCount());
        }
    }

    @Test
    public void dataValueListener() throws UaException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(2);

        ManagedDataItem dataItem = subscription.createDataItem(
            Identifiers.Server_ServerStatus_State,
            item -> {
                item.addDataValueListener((i, value) -> latch.countDown());

                ManagedDataItem.DataValueListener dataValueListener = (i, value) -> latch.countDown();
                item.addDataValueListener(dataValueListener);
            }
        );

        assertTrue(dataItem.getStatusCode().isGood());

        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void addRemoveDataValueListener() throws UaException {
        ManagedDataItem dataItem = subscription.createDataItem(
            Identifiers.Server_ServerStatus_State
        );

        ManagedDataItem.DataValueListener listener = (item, value) -> {};

        dataItem.addDataValueListener(listener);
        assertTrue(dataItem.removeDataValueListener(listener));

        listener = dataItem.addDataValueListener(value -> {});
        assertTrue(dataItem.removeDataValueListener(listener));
    }

}
