/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.siddhi.core.managment;

import org.apache.log4j.Logger;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.siddhi.core.event.stream.MetaStreamEvent;
import org.wso2.siddhi.core.event.stream.Operation;
import org.wso2.siddhi.core.event.stream.StreamEvent;
import org.wso2.siddhi.core.event.stream.StreamEventCloner;
import org.wso2.siddhi.core.event.stream.StreamEventPool;
import org.wso2.siddhi.core.event.stream.holder.SnapshotableStreamEventQueue;
import org.wso2.siddhi.core.util.snapshot.state.SnapshotState;
import org.wso2.siddhi.core.util.snapshot.state.SnapshotStateList;
import org.wso2.siddhi.query.api.definition.Attribute;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class SnapshotableEventsTestCase {
    private static final Logger log = Logger.getLogger(SnapshotableEventsTestCase.class);

    @BeforeMethod
    public void init() {
    }

    @Test
    public void incrementalPersistenceTest1() throws InterruptedException, IOException, ClassNotFoundException {

        MetaStreamEvent metaStreamEvent = new MetaStreamEvent();
        metaStreamEvent.addOutputData(new Attribute("symbol", Attribute.Type.STRING));
        metaStreamEvent.addOutputData(new Attribute("price", Attribute.Type.FLOAT));
        metaStreamEvent.addOutputData(new Attribute("volume", Attribute.Type.LONG));

        StreamEventCloner streamEventCloner = new StreamEventCloner(metaStreamEvent,
                new StreamEventPool(metaStreamEvent, 5));

        SnapshotableStreamEventQueue snapshotableStreamEventQueue =
                new SnapshotableStreamEventQueue(streamEventCloner);
        StreamEvent streamEvent = new StreamEvent(metaStreamEvent.getBeforeWindowData().size(),
                metaStreamEvent.getOnAfterWindowData().size(), metaStreamEvent.getOutputData().size());
        streamEvent.setOutputData(new Object[]{"IBM", 500.6f, 1});

        for (int i = 0; i < 20; i++) {
            streamEvent.getOutputData()[2] = i;
            snapshotableStreamEventQueue.add(streamEventCloner.copyStreamEvent(streamEvent));
        }

        HashMap<Long, String> snapshots = new HashMap<>();
        SnapshotState snapshot1 = snapshotableStreamEventQueue.getSnapshot();
        StreamEvent streamEvents = (StreamEvent) snapshot1.getState();
        Assert.assertTrue(streamEvents != null);
        snapshots.put(3L, toString(snapshot1));

        for (int i = 20; i < 40; i++) {
            streamEvent.getOutputData()[2] = i;
            snapshotableStreamEventQueue.add(streamEventCloner.copyStreamEvent(streamEvent));
        }

        SnapshotState snapshot2 = snapshotableStreamEventQueue.getSnapshot();
        ArrayList<Operation> operationLog = (ArrayList<Operation>) snapshot2.getState();
        Assert.assertTrue(operationLog != null);
        snapshots.put(4L, toString(snapshot2));

        for (int i = 40; i < 80; i++) {
            streamEvent.getOutputData()[2] = i;
            snapshotableStreamEventQueue.add(streamEventCloner.copyStreamEvent(streamEvent));
        }

        SnapshotState snapshot3 = snapshotableStreamEventQueue.getSnapshot();
        operationLog = (ArrayList<Operation>) snapshot3.getState();
        Assert.assertTrue(operationLog != null);
        snapshots.put(5L, toString(snapshot3));

        SnapshotableStreamEventQueue snapshotableStreamEventQueue2 =
                new SnapshotableStreamEventQueue(streamEventCloner);
        SnapshotStateList snapshotStateList = new SnapshotStateList();
        for (Map.Entry<Long, String> entry : snapshots.entrySet()) {
            snapshotStateList.putSnapshotState(entry.getKey(), (SnapshotState) fromString(entry.getValue()));
        }
        snapshotableStreamEventQueue2.restore(snapshotStateList);

        Assert.assertEquals(snapshotableStreamEventQueue, snapshotableStreamEventQueue2);

        for (int i = 80; i < 130; i++) {
            streamEvent.getOutputData()[2] = i;
            snapshotableStreamEventQueue.add(streamEventCloner.copyStreamEvent(streamEvent));
        }

        SnapshotState snapshot4 = snapshotableStreamEventQueue.getSnapshot();
        streamEvents = (StreamEvent) snapshot1.getState();
        Assert.assertTrue(streamEvents != null);
        snapshots = new HashMap<>();
        snapshots.put(6L, toString(snapshot4));

        SnapshotableStreamEventQueue snapshotableStreamEventQueue3 =
                new SnapshotableStreamEventQueue(streamEventCloner);
        snapshotStateList = new SnapshotStateList();
        for (Map.Entry<Long, String> entry : snapshots.entrySet()) {
            snapshotStateList.putSnapshotState(entry.getKey(), (SnapshotState) fromString(entry.getValue()));
        }
        snapshotableStreamEventQueue3.restore(snapshotStateList);

        Assert.assertEquals(snapshotableStreamEventQueue, snapshotableStreamEventQueue3);
    }

    /**
     * Read the object from Base64 string.
     */
    private static Object fromString(String s) throws IOException,
            ClassNotFoundException {
        byte[] data = Base64.getDecoder().decode(s);
        ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(data));
        Object o = ois.readObject();
        ois.close();
        return o;
    }

    /**
     * Write the object to a Base64 string.
     */
    private static String toString(Serializable o) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(o);
        oos.close();
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

}
