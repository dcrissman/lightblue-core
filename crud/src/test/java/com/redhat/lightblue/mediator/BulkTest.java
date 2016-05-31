/*
 Copyright 2013 Red Hat, Inc. and/or its affiliates.

 This file is part of lightblue.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.redhat.lightblue.mediator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.redhat.lightblue.*;
import com.redhat.lightblue.crud.*;
import com.redhat.lightblue.metadata.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.Semaphore;
import org.junit.Ignore;

public class BulkTest extends AbstractMediatorTest {

    public interface FindCb {
        Response call(FindRequest req);
    }

    public interface InsertCb {
        Response call(InsertionRequest req);
    }

    public class TestMediator extends Mediator {

        FindCb findCb = null;
        InsertCb insertCb = null;

        public TestMediator(Metadata md, Factory f) {
            super(md, f);
        }

        @Override
        public Response find(FindRequest req) {
            if (findCb != null) {
                return findCb.call(req);
            } else {
                return super.find(req);
            }
        }

        @Override
        public Response insert(InsertionRequest req) {
            if (insertCb != null) {
                return insertCb.call(req);
            } else {
                return super.insert(req);
            }
        }
    }

    @Override
    protected Mediator newMediator(Metadata md, Factory f) {
        return new TestMediator(md, f);
    }

    @Test
    public void bulkTest() throws Exception {
        BulkRequest breq = new BulkRequest();

        InsertionRequest ireq = new InsertionRequest();
        ireq.setEntityVersion(new EntityVersion("test", "1.0"));
        ireq.setEntityData(loadJsonNode("./sample1.json"));
        ireq.setReturnFields(null);
        ireq.setClientId(new RestClientIdentification(Arrays.asList("test-insert", "test-update")));
        breq.add(ireq);

        FindRequest freq = new FindRequest();
        freq.setEntityVersion(new EntityVersion("test", "1.0"));
        breq.add(freq);

        mdManager.md.getAccess().getFind().setRoles("role1");

        BulkResponse bresp = mediator.bulkRequest(breq);

        Response response = bresp.getEntries().get(0);
        Assert.assertEquals(OperationStatus.COMPLETE, response.getStatus());
        Assert.assertEquals(1, response.getModifiedCount());
        Assert.assertEquals(0, response.getMatchCount());
        Assert.assertEquals(0, response.getDataErrors().size());
        Assert.assertEquals(0, response.getErrors().size());

        response = bresp.getEntries().get(1);

        Assert.assertEquals(OperationStatus.ERROR, response.getStatus());
        Assert.assertEquals(0, response.getModifiedCount());
        Assert.assertEquals(0, response.getMatchCount());
        Assert.assertEquals(0, response.getDataErrors().size());
        Assert.assertEquals(1, response.getErrors().size());
        Assert.assertEquals(CrudConstants.ERR_NO_ACCESS, response.getErrors().get(0).getErrorCode());
    }

    @Test
    @Ignore
    public void bulkTest_fromJson() throws Exception {
        // TODO stop ignoring and finish impl once JSON schema is defined
        BulkRequest breq = new BulkRequest();

        InsertionRequest ireq = new InsertionRequest();
        ireq.setEntityVersion(new EntityVersion("test", "1.0"));
        ireq.setEntityData(loadJsonNode("./sample1.json"));
        ireq.setReturnFields(null);
        ireq.setClientId(new RestClientIdentification(Arrays.asList("test-insert", "test-update")));
        breq.add(ireq);

        FindRequest freq = new FindRequest();
        freq.setEntityVersion(new EntityVersion("test", "1.0"));
        breq.add(freq);

        // convert to string then parse back.  should be identical
        ObjectNode breqJson = (ObjectNode) breq.toJson();

        BulkRequest breqParsed = BulkRequest.fromJson(breqJson);

        System.out.println("asdf");
    }

    private class PFindCb implements FindCb {
        Semaphore sem = new Semaphore(0);
        int nested = 0;

        @Override
        public Response call(FindRequest req) {
            nested++;
            try {
                sem.acquire();
                return new Response();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                nested--;
            }
        }
    }

    private class PInsertCb implements InsertCb {
        Semaphore sem = new Semaphore(0);

        @Override
        public Response call(InsertionRequest req) {
            try {
                sem.acquire();
                return new Response();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private abstract class ValidatorThread extends Thread {
        PFindCb find;
        PInsertCb insert;
        boolean valid = false;

        public ValidatorThread(PFindCb f, PInsertCb i) {
            find = f;
            insert = i;
        }

    }

    @Test
    public void parallelBulkTest() throws Exception {
        BulkRequest breq = new BulkRequest();

        FindRequest freq = new FindRequest();
        freq.setEntityVersion(new EntityVersion("test", "1.0"));
        freq.setClientId(new RestClientIdentification(Arrays.asList("test-find")));

        InsertionRequest ireq = new InsertionRequest();
        ireq.setEntityVersion(new EntityVersion("test", "1.0"));
        ireq.setEntityData(loadJsonNode("./sample1.json"));
        ireq.setReturnFields(null);
        ireq.setClientId(new RestClientIdentification(Arrays.asList("test-insert", "test-update")));

        breq.add(freq);
        breq.add(freq);
        breq.add(freq);
        breq.add(ireq);
        breq.add(freq);
        breq.add(freq);
        breq.add(ireq);

        PFindCb findCb = new PFindCb();
        PInsertCb insertCb = new PInsertCb();
        ((TestMediator) mediator).findCb = findCb;
        ((TestMediator) mediator).insertCb = insertCb;

        ValidatorThread validator = new ValidatorThread(findCb, insertCb) {
            @Override
            public void run() {
                try {
                    // Check if all 3 finds are waiting
                    while (find.nested < 3) {
                        Thread.sleep(1);
                    }
                    // Let the 3 find requests complete
                    find.sem.release(3);
                    // Busy wait
                    while (find.sem.availablePermits() > 0) {
                        Thread.sleep(1);
                    }
                    // Let insert complete
                    insert.sem.release(1);
                    while (insert.sem.availablePermits() > 0) {
                        Thread.sleep(1);
                    }
                    // Check if all 2 finds are waiting
                    while (find.nested < 2) {
                        Thread.sleep(1);
                    }
                    // Let the remaining 2 find requests complete
                    find.sem.release(2);
                    while (find.sem.availablePermits() > 0) {
                        Thread.sleep(1);
                    }
                    insert.sem.release(1);
                    while (insert.sem.availablePermits() > 0) {
                        Thread.sleep(1);
                    }
                    valid = true;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        validator.start();

        BulkResponse bresp = mediator.bulkRequest(breq);
        validator.join();

        Assert.assertTrue(validator.valid);
    }
}
