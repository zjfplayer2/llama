/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cloudera.llama.am.impl;

import com.cloudera.llama.am.api.LlamaAM;
import com.cloudera.llama.am.api.LlamaAMEvent;
import com.cloudera.llama.am.api.LlamaAMException;
import com.cloudera.llama.am.api.LlamaAMListener;
import com.cloudera.llama.am.api.PlacedReservation;
import com.cloudera.llama.am.api.PlacedResource;
import com.cloudera.llama.am.api.RMResource;
import com.cloudera.llama.am.api.Reservation;
import com.cloudera.llama.am.api.Resource;
import com.cloudera.llama.am.api.TestUtils;
import com.cloudera.llama.am.spi.RMLlamaAMCallback;
import com.cloudera.llama.am.spi.RMLlamaAMConnector;
import com.cloudera.llama.am.spi.RMResourceChange;
import com.cloudera.llama.util.UUID;
import junit.framework.Assert;
import org.apache.hadoop.conf.Configuration;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class TestSingleQueueLlamaAM {

  public static class MyRMLlamaAMConnector implements RMLlamaAMConnector {

    public boolean start = false;
    public boolean stop = false;
    public boolean reserve = false;
    public boolean release = false;
    public RMLlamaAMCallback callback;

    protected MyRMLlamaAMConnector() {
    }

    @Override
    public void setLlamaAMCallback(RMLlamaAMCallback callback) {
      this.callback = callback;
    }

    @Override
    public void start() throws LlamaAMException {
    }

    @Override
    public void stop() {
    }

    @Override
    public void register(String queue) throws LlamaAMException {
      start = true;
    }

    @Override
    public void unregister() {
      stop = true;
    }

    @Override
    public List<String> getNodes() throws LlamaAMException {
      return Arrays.asList("node");
    }

    @Override
    public void reserve(Collection<RMResource> resources)
        throws LlamaAMException {
      reserve = true;
    }

    @Override
    public void release(Collection<RMResource> resources)
        throws LlamaAMException {
      release = true;
    }

    @Override
    public boolean reassignResource(Object rmResourceId, UUID resourceId) {
      return false;
    }

  }

  public static class DummyLlamaAMListener implements LlamaAMListener {
    public List<LlamaAMEvent> events = new ArrayList<LlamaAMEvent>();

    @Override
    public void handle(LlamaAMEvent event) {
      events.add(event);
    }
  }

  public static class DummySingleQueueLlamaAMCallback implements
      SingleQueueLlamaAM.Callback {

    @Override
    public void discardReservation(UUID reservationId) {
    }

    @Override
    public void discardAM(String queue) {
    }
  }

  private SingleQueueLlamaAM createLlamaAM() {
    Configuration conf = new Configuration(false);
    conf.setClass(LlamaAM.RM_CONNECTOR_CLASS_KEY, MyRMLlamaAMConnector.class,
        RMLlamaAMConnector.class);
    conf.setBoolean(LlamaAM.RESOURCES_CACHING_ENABLED_KEY, false);
    return new SingleQueueLlamaAM(conf, "queue",
        new DummySingleQueueLlamaAMCallback());
  }

  @Test
  public void testRmStartStop() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    try {
      Assert.assertFalse(llama.isRunning());
      llama.start();
      Assert.assertTrue(((MyRMLlamaAMConnector) llama.getRMConnector()).start);
      Assert.assertTrue(llama.isRunning());
      Assert.assertFalse(((MyRMLlamaAMConnector) llama.getRMConnector()).stop);
    } finally {
      llama.stop();
      Assert.assertFalse(llama.isRunning());
      Assert.assertTrue(((MyRMLlamaAMConnector) llama.getRMConnector()).stop);
      llama.stop();
    }
  }

  @Test
  public void testRmStopNoRMConnector() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    llama.stop();
  }

  private static final Resource RESOURCE1 = TestUtils.createResource(
      "n1", Resource.Locality.DONT_CARE, 1, 1024);

  private static final Resource RESOURCE2 = TestUtils.createResource(
      "n2", Resource.Locality.PREFERRED, 2, 2048);

  private static final Resource RESOURCE3 = TestUtils.createResource(
      "n3", Resource.Locality.PREFERRED, 3, 2048);

  private static final List<Resource> RESOURCES1 = Arrays.asList(RESOURCE1);

  private static final List<Resource> RESOURCES2 = Arrays.asList(RESOURCE1,
      RESOURCE2);

  private static final Reservation RESERVATION1_GANG = 
      TestUtils.createReservation(UUID.randomUUID(), "u", "queue", RESOURCES1, 
          true);

  private static final Reservation RESERVATION2_GANG = 
      TestUtils.createReservation(UUID.randomUUID(), "u","queue", RESOURCES2, 
          true);

  private static final Reservation RESERVATION1_NONGANG = 
      TestUtils.createReservation(UUID.randomUUID(), "u","queue", RESOURCES1, 
          false);

  private static final Reservation RESERVATION2_NONGANG = 
      TestUtils.createReservation(UUID.randomUUID(), "u","queue", RESOURCES2, 
          false);

  @Test
  public void testGetNode() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    try {
      llama.start();
      llama.reserve(RESERVATION1_NONGANG).getReservationId();
      Assert.assertEquals(Arrays.asList("node"), llama.getNodes());
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testRmReserve() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    try {
      llama.start();
      UUID reservationId = llama.reserve(RESERVATION1_NONGANG).getReservationId();

      Assert.assertTrue(((MyRMLlamaAMConnector) llama.getRMConnector()).reserve);
      Assert.assertFalse(((MyRMLlamaAMConnector) llama.getRMConnector()).release);

      PlacedReservation placedReservation = llama.getReservation(reservationId);
      Assert.assertNotNull(placedReservation);
      Assert.assertEquals(PlacedReservation.Status.PENDING,
          placedReservation.getStatus());
      Assert.assertEquals(reservationId, placedReservation.getReservationId());
      Assert.assertEquals("queue", placedReservation.getQueue());
      Assert.assertFalse(placedReservation.isGang());
      Assert.assertEquals(1, placedReservation.getResources().size());
      PlacedResource resource = placedReservation.getPlacedResources().get(0);
      Assert.assertEquals(PlacedResource.Status.PENDING, resource.getStatus());
      Assert.assertEquals(-1, resource.getCpuVCores());
      Assert.assertEquals(-1, resource.getMemoryMbs());
      Assert.assertEquals(null, resource.getLocation());
      Assert.assertEquals("queue", resource.getQueue());
      Assert.assertEquals(reservationId, resource.getReservationId());
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testRmRelease() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    try {
      llama.start();
      UUID reservationId = llama.reserve(RESERVATION1_NONGANG).getReservationId();
      Assert.assertNotNull(llama.releaseReservation(RESERVATION1_NONGANG.getHandle(), reservationId));
      Assert.assertNull(llama.releaseReservation(RESERVATION1_NONGANG.getHandle(), reservationId));
      Assert.assertTrue(((MyRMLlamaAMConnector) llama.getRMConnector()).release);
      Assert.assertNull(llama._getReservation(reservationId));
    } finally {
      llama.stop();
    }
  }

  @Test(expected = LlamaAMException.class)
  public void testReleaseUsingWrongHandle() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    try {
      llama.start();
      UUID reservationId = llama.reserve(RESERVATION1_NONGANG).getReservationId();
      llama.releaseReservation(UUID.randomUUID(), reservationId);
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testAdminRelease() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    try {
      llama.start();
      UUID reservationId = llama.reserve(RESERVATION1_NONGANG).getReservationId();
      Assert.assertNotNull(llama.releaseReservation(LlamaAM.ADMIN_HANDLE, reservationId));
      Assert.assertTrue(((MyRMLlamaAMConnector) llama.getRMConnector()).release);
      Assert.assertNull(llama._getReservation(reservationId));
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testFullyAllocateReservationNoGangOneResource() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION1_NONGANG).getReservationId();
      PlacedReservation pr = llama.getReservation(reservationId);
      UUID resource1Id = pr.getPlacedResources().get(0).getResourceId();
      RMResourceChange change = RMResourceChange.createResourceAllocation
          (resource1Id, "cid1", 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change));
      Assert.assertEquals(1,
          listener.events.get(0).getAllocatedResources().size());
      Assert.assertEquals(1,
          listener.events.get(0).getAllocatedReservationIds().size
              ());
      PlacedResource resource =
          listener.events.get(0).getAllocatedResources().get(0);
      Assert.assertEquals(pr.getPlacedResources().get(0).getRmResourceId(),
          resource.getRmResourceId());
      Assert.assertEquals(3, resource.getCpuVCores());
      Assert.assertEquals(4096, resource.getMemoryMbs());
      Assert.assertEquals("a1", resource.getLocation());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.ALLOCATED,
          reservation.getStatus());
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testFullyAllocateReservationGangOneResource() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION1_GANG).getReservationId();
      PlacedReservation pr = llama.getReservation(reservationId);
      UUID resource1Id = pr.getPlacedResources().get(0).getResourceId();
      RMResourceChange change = RMResourceChange.createResourceAllocation
          (resource1Id, new Object(), 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change));
      Assert.assertEquals(1,
          listener.events.get(0).getAllocatedResources().size());
      Assert.assertEquals(1,
          listener.events.get(0).getAllocatedReservationIds().size
              ());

      PlacedResource resource =
          listener.events.get(0).getAllocatedResources().get(0);
      Assert.assertEquals(pr.getPlacedResources().get(0).getRmResourceId(),
          resource.getRmResourceId());
      Assert.assertEquals(3, resource.getCpuVCores());
      Assert.assertEquals(4096, resource.getMemoryMbs());
      Assert.assertEquals("a1", resource.getLocation());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.ALLOCATED,
          reservation.getStatus());
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testFullyAllocateReservationNoGangTwoResources()
      throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_NONGANG).getReservationId();
      PlacedReservation pr = llama.getReservation(reservationId);
      UUID resource1Id = pr.getPlacedResources().get(0).getResourceId();
      UUID resource2Id = pr.getPlacedResources().get(1).getResourceId();
      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (resource1Id, new Object(), 3, 4096, "a1");
      RMResourceChange change2 = RMResourceChange.createResourceAllocation
          (resource2Id, new Object(), 4, 5112, "a2");
      llama.changesFromRM(Arrays.asList(change1, change2));
      Assert.assertEquals(2,
          listener.events.get(0).getAllocatedResources().size());
      Assert.assertEquals(1,
          listener.events.get(0).getAllocatedReservationIds().size
              ());
      PlacedResource resource1 =
          listener.events.get(0).getAllocatedResources().get(0);
      Assert.assertEquals(pr.getPlacedResources().get(0).getRmResourceId(),
          resource1.getRmResourceId());
      PlacedResource resource2 =
          listener.events.get(0).getAllocatedResources().get(1);
      Assert.assertEquals(pr.getPlacedResources().get(1).getRmResourceId(),
          resource2.getRmResourceId());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.ALLOCATED,
          reservation.getStatus());
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testFullyAllocateReservationGangTwoResources() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_GANG).getReservationId();
      PlacedReservation pr = llama.getReservation(reservationId);
      UUID resource1Id = pr.getPlacedResources().get(0).getResourceId();
      UUID resource2Id = pr.getPlacedResources().get(1).getResourceId();
      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (resource1Id, new Object(), 3, 4096, "a1");
      RMResourceChange change2 = RMResourceChange.createResourceAllocation
          (resource2Id, new Object(), 4, 5112, "a2");
      llama.changesFromRM(Arrays.asList(change1, change2));
      Assert.assertEquals(2,
          listener.events.get(0).getAllocatedResources().size());
      Assert.assertEquals(1,
          listener.events.get(0).getAllocatedReservationIds().size
              ());
      PlacedResource resource1 =
          listener.events.get(0).getAllocatedResources().get(0);
      Assert.assertEquals(pr.getPlacedResources().get(0).getRmResourceId(),
          resource1.getRmResourceId());
      PlacedResource resource2 =
          listener.events.get(0).getAllocatedResources().get(1);
      Assert.assertEquals(pr.getPlacedResources().get(1).getRmResourceId(),
          resource2.getRmResourceId());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.ALLOCATED,
          reservation.getStatus());

    } finally {
      llama.stop();
    }
  }

  @Test
  public void testPartiallyThenFullyAllocateReservationNoGangTwoResources()
      throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_NONGANG).getReservationId();
      PlacedReservation pr = llama.getReservation(reservationId);
      UUID resource1Id = pr.getPlacedResources().get(0).getResourceId();
      UUID resource2Id = pr.getPlacedResources().get(1).getResourceId();
      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (resource1Id, new Object(), 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      Assert.assertEquals(1,
          listener.events.get(0).getAllocatedResources().size());
      Assert.assertEquals(0,
          listener.events.get(0).getAllocatedReservationIds().size
              ());
      PlacedResource resource1 =
          listener.events.get(0).getAllocatedResources().get(0);
      Assert.assertEquals(pr.getPlacedResources().get(0).getRmResourceId(),
          resource1.getRmResourceId());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.PARTIAL,
          reservation.getStatus());
      RMResourceChange change2 = RMResourceChange.createResourceAllocation
          (resource2Id, new Object(), 4, 5112, "a2");
      listener.events.clear();
      llama.changesFromRM(Arrays.asList(change2));
      Assert.assertEquals(1,
          listener.events.get(0).getAllocatedResources().size());
      Assert.assertEquals(1,
          listener.events.get(0).getAllocatedReservationIds().size
              ());
      PlacedResource resource2 =
          listener.events.get(0).getAllocatedResources().get(0);
      Assert.assertEquals(pr.getPlacedResources().get(1).getRmResourceId(),
          resource2.getRmResourceId());
      reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.ALLOCATED,
          reservation.getStatus());

    } finally {
      llama.stop();
    }
  }

  @Test
  public void testPartiallyThenFullyAllocateReservationGangTwoResources()
      throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_GANG).getReservationId();
      PlacedReservation pr = llama.getReservation(reservationId);
      UUID resource1Id = pr.getPlacedResources().get(0).getResourceId();
      UUID resource2Id = pr.getPlacedResources().get(1).getResourceId();
      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (resource1Id, new Object(), 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      Assert.assertFalse(listener.events.isEmpty());
      Assert.assertTrue(listener.events.get(0).isEmpty());
      listener.events.clear();
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.PARTIAL,
          reservation.getStatus());
      RMResourceChange change2 = RMResourceChange.createResourceAllocation
          (resource2Id, new Object(), 4, 5112, "a2");
      llama.changesFromRM(Arrays.asList(change2));
      Assert.assertNotNull(listener.events.get(0));
      Assert.assertEquals(2,
          listener.events.get(0).getAllocatedResources().size());
      Assert.assertEquals(1,
          listener.events.get(0).getAllocatedReservationIds().size
              ());
      PlacedResource resource1 =
          listener.events.get(0).getAllocatedResources().get(0);
      Assert.assertEquals(pr.getPlacedResources().get(0).getRmResourceId(),
          resource1.getRmResourceId());
      PlacedResource resource2 =
          listener.events.get(0).getAllocatedResources().get(1);
      Assert.assertEquals(pr.getPlacedResources().get(1).getRmResourceId(),
          resource2.getRmResourceId());
      reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.ALLOCATED,
          reservation.getStatus());

    } finally {
      llama.stop();
    }
  }

  @Test
  public void testRejectPendingReservation() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION1_GANG).getReservationId();
      PlacedReservation pr = llama.getReservation(reservationId);
      UUID resource1Id = pr.getPlacedResources().get(0).getResourceId();
      RMResourceChange change = RMResourceChange.createResourceChange
          (resource1Id, PlacedResource.Status.REJECTED);
      llama.changesFromRM(Arrays.asList(change));
      Assert.assertEquals(1,
          listener.events.get(0).getRejectedClientResourcesIds()
              .size());
      Assert.assertEquals(1,
          listener.events.get(0).getRejectedReservationIds().size());
      Assert.assertNull(llama.getReservation(reservationId));
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testRejectPartialGangReservation() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_GANG).getReservationId();
      PlacedReservation pr = llama.getReservation(reservationId);
      UUID resource1Id = pr.getPlacedResources().get(0).getResourceId();
      UUID resource2Id = pr.getPlacedResources().get(1).getResourceId();
      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (resource1Id, new Object(), 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (resource2Id, PlacedResource.Status.REJECTED);
      listener.events.clear();
      llama.changesFromRM(Arrays.asList(change2));
      Assert.assertEquals(1,
          listener.events.get(0).getRejectedClientResourcesIds()
              .size());
      Assert.assertEquals(1,
          listener.events.get(0).getRejectedReservationIds().size());
      Assert.assertNull(llama.getReservation(reservationId));
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testRejectPartialNonGangReservation() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_NONGANG).getReservationId();
      PlacedReservation pr = llama.getReservation(reservationId);
      UUID resource1Id = pr.getPlacedResources().get(0).getResourceId();
      UUID resource2Id = pr.getPlacedResources().get(1).getResourceId();
      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (resource1Id, new Object(), 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (resource2Id, PlacedResource.Status.REJECTED);
      listener.events.clear();
      llama.changesFromRM(Arrays.asList(change2));
      Assert.assertEquals(1,
          listener.events.get(0).getRejectedClientResourcesIds()
              .size());
      Assert.assertEquals(0,
          listener.events.get(0).getRejectedReservationIds().size());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.PARTIAL,
          reservation.getStatus());
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testPreemptPartialGangReservation() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_GANG).getReservationId();
      PlacedReservation pr = llama.getReservation(reservationId);
      UUID resource1Id = pr.getPlacedResources().get(0).getResourceId();
      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (resource1Id, new Object(), 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (resource1Id, PlacedResource.Status.PREEMPTED);
      listener.events.clear();
      llama.changesFromRM(Arrays.asList(change2));
      Assert.assertEquals(1,
          listener.events.get(0).getRejectedReservationIds().size());
      Assert.assertEquals(0,
          listener.events.get(0).getPreemptedClientResourceIds()
              .size());
      Assert.assertNull(llama.getReservation(reservationId));
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testPreemptPartialNonGangReservation() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_NONGANG).getReservationId();
      PlacedReservation pr = llama.getReservation(reservationId);
      UUID resource1Id = pr.getPlacedResources().get(0).getResourceId();
      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (resource1Id, new Object(), 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (resource1Id, PlacedResource.Status.PREEMPTED);
      listener.events.clear();
      llama.changesFromRM(Arrays.asList(change2));
      Assert.assertEquals(1,
          listener.events.get(0).getPreemptedClientResourceIds()
              .size());
      Assert.assertEquals(0,
          listener.events.get(0).getPreemptedReservationIds().size
              ());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.PARTIAL,
          reservation.getStatus());
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testPreemptAllocatedGangReservation() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION1_GANG).getReservationId();
      PlacedReservation pr = llama.getReservation(reservationId);
      UUID resource1Id = pr.getPlacedResources().get(0).getResourceId();
      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (resource1Id, new Object(), 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (resource1Id, PlacedResource.Status.PREEMPTED);
      listener.events.clear();
      llama.changesFromRM(Arrays.asList(change2));
      Assert.assertEquals(0,
          listener.events.get(0).getRejectedReservationIds().size());
      Assert.assertEquals(0,
          listener.events.get(0).getPreemptedReservationIds().size
              ());
      Assert.assertEquals(1,
          listener.events.get(0).getPreemptedClientResourceIds()
              .size());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.ALLOCATED,
          reservation.getStatus());
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testPreemptAllocatedNonGangReservation() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION1_NONGANG).getReservationId();
      PlacedReservation pr = llama.getReservation(reservationId);
      UUID resource1Id = pr.getPlacedResources().get(0).getResourceId();
      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (resource1Id, new Object(), 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (resource1Id, PlacedResource.Status.PREEMPTED);
      listener.events.clear();
      llama.changesFromRM(Arrays.asList(change2));
      Assert.assertEquals(0,
          listener.events.get(0).getRejectedReservationIds().size());
      Assert.assertEquals(0,
          listener.events.get(0).getPreemptedReservationIds().size
              ());
      Assert.assertEquals(1,
          listener.events.get(0).getPreemptedClientResourceIds()
              .size());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.ALLOCATED,
          reservation.getStatus());
    } finally {
      llama.stop();
    }
  }


  @Test
  public void testLostPartialGangReservation() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_GANG).getReservationId();
      PlacedReservation pr = llama.getReservation(reservationId);
      UUID resource1Id = pr.getPlacedResources().get(0).getResourceId();
      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (resource1Id, new Object(), 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (resource1Id, PlacedResource.Status.LOST);
      listener.events.clear();
      llama.changesFromRM(Arrays.asList(change2));
      Assert.assertEquals(1,
          listener.events.get(0).getRejectedReservationIds().size());
      Assert.assertEquals(0,
          listener.events.get(0).getPreemptedClientResourceIds()
              .size());
      Assert.assertNull(llama.getReservation(reservationId));
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testLostPartialNonGangReservation() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION2_NONGANG).getReservationId();
      PlacedReservation pr = llama.getReservation(reservationId);
      UUID resource1Id = pr.getPlacedResources().get(0).getResourceId();
      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (resource1Id, new Object(), 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (resource1Id, PlacedResource.Status.LOST);
      listener.events.clear();
      llama.changesFromRM(Arrays.asList(change2));
      Assert.assertEquals(1,
          listener.events.get(0).getLostClientResourcesIds().size());
      Assert.assertEquals(0,
          listener.events.get(0).getRejectedReservationIds().size());
      Assert.assertEquals(0,
          listener.events.get(0).getPreemptedReservationIds().size
              ());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.PARTIAL,
          reservation.getStatus());
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testLostAllocatedGangReservation() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION1_GANG).getReservationId();
      PlacedReservation pr = llama.getReservation(reservationId);
      UUID resource1Id = pr.getPlacedResources().get(0).getResourceId();
      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (resource1Id, new Object(), 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (resource1Id, PlacedResource.Status.LOST);
      listener.events.clear();
      llama.changesFromRM(Arrays.asList(change2));
      Assert.assertEquals(0,
          listener.events.get(0).getRejectedReservationIds().size());
      Assert.assertEquals(0,
          listener.events.get(0).getPreemptedReservationIds().size
              ());
      Assert.assertEquals(1,
          listener.events.get(0).getLostClientResourcesIds().size());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.ALLOCATED,
          reservation.getStatus());
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testLostAllocatedNonGangReservation() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      llama.start();
      llama.addListener(listener);
      UUID reservationId = llama.reserve(RESERVATION1_NONGANG).getReservationId();
      PlacedReservation pr = llama.getReservation(reservationId);
      UUID resource1Id = pr.getPlacedResources().get(0).getResourceId();
      RMResourceChange change1 = RMResourceChange.createResourceAllocation
          (resource1Id, new Object(), 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      RMResourceChange change2 = RMResourceChange.createResourceChange
          (resource1Id, PlacedResource.Status.LOST);
      listener.events.clear();
      llama.changesFromRM(Arrays.asList(change2));
      Assert.assertEquals(0,
          listener.events.get(0).getRejectedReservationIds().size());
      Assert.assertEquals(0,
          listener.events.get(0).getPreemptedReservationIds().size
              ());
      Assert.assertEquals(1,
          listener.events.get(0).getLostClientResourcesIds().size());
      PlacedReservation reservation = llama.getReservation(reservationId);
      Assert.assertNotNull(reservation);
      Assert.assertEquals(PlacedReservation.Status.ALLOCATED,
          reservation.getStatus());
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testUnknownResourceRmChange() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      RMResourceChange change1 = RMResourceChange.createResourceAllocation(
          UUID.randomUUID(), new Object(), 3, 4096, "a1");
      llama.changesFromRM(Arrays.asList(change1));
      Assert.assertTrue(listener.events.isEmpty());
    } finally {
      llama.stop();
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testRmChangesNull() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    try {
      llama.start();
      llama.changesFromRM(null);
    } finally {
      llama.stop();
    }
  }

  @Test
  public void testReleaseReservationsForHandle() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    try {
      llama.start();
      UUID cId1 = UUID.randomUUID();
      UUID cId2 = UUID.randomUUID();
      UUID reservationId1 = llama.reserve(TestUtils.createReservation(cId1, "u",
          "queue", Arrays.asList(RESOURCE1), true)).getReservationId();
      UUID reservationId2 = llama.reserve(TestUtils.createReservation(cId1, "u",
          "queue", Arrays.asList(RESOURCE2), true)).getReservationId();
      UUID reservationId3 = llama.reserve(TestUtils.createReservation(cId2, "u",
          "queue", Arrays.asList(RESOURCE3), true)).getReservationId();
      Assert.assertNotNull(llama._getReservation(reservationId1));
      Assert.assertNotNull(llama._getReservation(reservationId2));
      Assert.assertNotNull(llama._getReservation(reservationId3));
      llama.releaseReservationsForHandle(cId1);
      Assert.assertNull(llama._getReservation(reservationId1));
      Assert.assertNull(llama._getReservation(reservationId2));
      Assert.assertNotNull(llama._getReservation(reservationId3));
    } finally {
      llama.stop();
    }
  }


  @Test
  public void testLoseAllReservations() throws Exception {
    SingleQueueLlamaAM llama = createLlamaAM();
    DummyLlamaAMListener listener = new DummyLlamaAMListener();
    try {
      llama.start();
      llama.addListener(listener);
      UUID cId1 = UUID.randomUUID();
      UUID cId2 = UUID.randomUUID();
      UUID reservationId1 = llama.reserve(TestUtils.createReservation(cId1, "u",
          "queue", Arrays.asList(RESOURCE1), true)).getReservationId();
      UUID reservationId2 = llama.reserve(TestUtils.createReservation(cId1, "u",
          "queue", Arrays.asList(RESOURCE2), true)).getReservationId();
      UUID reservationId3 = llama.reserve(TestUtils.createReservation(cId2, "u",
          "queue", Arrays.asList(RESOURCE3), true)).getReservationId();
      llama.loseAllReservations();
      Assert.assertNull(llama._getReservation(reservationId1));
      Assert.assertNull(llama._getReservation(reservationId2));
      Assert.assertNull(llama._getReservation(reservationId3));
      Assert.assertEquals(2, listener.events.size());
      Assert.assertEquals(3,
          listener.events.get(0).getRejectedReservationIds().size() +
              listener.events.get(1).getRejectedReservationIds().size());
    } finally {
      llama.stop();
    }
  }
}
