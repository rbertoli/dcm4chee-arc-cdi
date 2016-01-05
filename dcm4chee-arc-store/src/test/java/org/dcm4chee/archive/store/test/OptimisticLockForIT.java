/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2012
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chee.archive.store.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.concurrent.TimeUnit;

import javax.ejb.EJBTransactionRolledbackException;
import javax.inject.Inject;
import javax.persistence.OptimisticLockException;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.io.SAXReader;
import org.dcm4che3.net.Device;
import org.dcm4chee.archive.conf.ArchiveAEExtension;
import org.dcm4chee.archive.conf.StoreParam;
import org.dcm4chee.archive.dto.GenericParticipant;
import org.dcm4chee.archive.entity.Code;
import org.dcm4chee.archive.patient.NonUniquePatientException;
import org.dcm4chee.archive.patient.PatientCircularMergedException;
import org.dcm4chee.archive.store.StoreContext;
import org.dcm4chee.archive.store.StoreService;
import org.dcm4chee.archive.store.StoreSession;
import org.dcm4chee.storage.conf.StorageSystem;
import org.dcm4chee.storage.conf.StorageSystemGroup;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Hesham Elbadawi <bsdreko@gmail.com>
 */
@RunWith(Arquillian.class)
public class OptimisticLockForIT extends BaseStoreIT {

    private static final String SOURCE_AET = "SOURCE_AET";
    private static final String[] RETRIEVE_AETS = { "RETRIEVE_AET" };
    private static final Logger log = LoggerFactory.getLogger(OptimisticLockForIT.class);

    private final int SEMAPHORE_WAIT = 10;
    private final boolean[] optimisticLockExceptionWasThrown = new boolean[] { false };

    @Inject
    StoreService storeService;

    @Inject
    SemaphoreHolder semaphores;

    @Inject
    private Device device;
    
    private static final String INSTANCE1 = "testdata/concurrent-1.xml";
    private static final String INSTANCE1a = "testdata/concurrent-1a.xml";
    private static final String INSTANCE1b = "testdata/concurrent-1b.xml";
    
    private static final String[] PIDS = new String[]{"STORE_SERVICE_TEST"};
    private static final String[] ISSUERS = new String[]{"DCM4CHEE_TESTDATA"};

    @Before
    public void setup() throws Exception {
        log.info("load patient");
        callUpdateOnThread(INSTANCE1).start();
        if (!semaphores.getReady().tryAcquire(1, SEMAPHORE_WAIT,
                TimeUnit.SECONDS))
            throw new RuntimeException("Child threads got broken somewhere - 1");
        semaphores.getDone().release();
    }
    
    @After
    public void terminate() throws SecurityException, IllegalStateException,
            RollbackException, HeuristicMixedException,
            HeuristicRollbackException, SystemException, NotSupportedException, NonUniquePatientException, PatientCircularMergedException {
        utx.begin();
        super.clearTestData(PIDS, ISSUERS, new Code[0]);
        utx.commit();
        em.clear();
    }

    @Deployment
    public static WebArchive createDeployment() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, "test.war");
        ITHelper.addDefaultDependenciesToWebArchive(war, new File(
                    "src/test/resources/testdata/beans.xml"));

        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "test.jar");
        jar.addClass(BaseStoreIT.class);
        jar.addClass(OptimisticLockForIT.class);
        jar.addClass(ParamFactory.class);
        jar.addClass(ConcurrentStoreServiceDecorator.class);
        jar.addClass(SemaphoreHolder.class);
        jar.addClass(SemaphoreHolderImpl.class);

        jar.addAsResource(INSTANCE1);
        jar.addAsResource(INSTANCE1a);
        jar.addAsResource(INSTANCE1b);
        jar.addAsManifestResource(new File(
                "src/test/resources/testdata/beans.xml"), "beans.xml");

        war.addAsLibrary(jar);
//        war.as(ZipExporter.class).exportTo(
//                new File("test.war"), true);

        return war;
    }

    @Test
    public void test() throws Exception {

        log.info("Started test");

        // launch concurrent modifications
        callUpdateOnThread(INSTANCE1a).start();
        callUpdateOnThread(INSTANCE1b).start();

        // wait until all of them are ready to commit
        // by trying to acquire N master's semaphore permits, where each
        // modifier will release one permit
        if (!semaphores.getReady().tryAcquire(2, SEMAPHORE_WAIT,
                TimeUnit.SECONDS))
            throw new RuntimeException("Child threads got broken somewhere - 1");

        // let the children finish the job, each of them will need 1 permit
        semaphores.getDone().release(3);

        // wait until one of the threads have eventually collected the
        // OptimisticLockException
        if (!semaphores.getReady().tryAcquire(2, SEMAPHORE_WAIT,
                TimeUnit.SECONDS))
            throw new RuntimeException("Child threads got broken somewhere - 1");

        Thread.sleep(10000);
        
        assertTrue("OptimisticLockException Should Not Be Thrown (retry failed)",
                !optimisticLockExceptionWasThrown[0]);
    }

    private StoreContext createStoreContext(String instance) throws Exception {

        StoreParam storeParam = ParamFactory.createStoreParam();

        StoreSession session = storeService.createStoreSession(storeService);
        session.setStoreParam(storeParam);
        StorageSystem storageSystem = new StorageSystem();
        storageSystem.setStorageSystemID("test_storage_system");
        StorageSystemGroup grp = new StorageSystemGroup();
        grp.setGroupID("test_grp");
        grp.addStorageSystem(storageSystem);
        session.setStorageSystem(storageSystem);
        session.setSource(new GenericParticipant("localhost", "testidentity"));
        session.setRemoteAET(SOURCE_AET);
        
        session.setArchiveAEExtension(device.getApplicationEntity("DCM4CHEE")
                .getAEExtension(ArchiveAEExtension.class));

        StoreContext storeContext = storeService.createStoreContext(session);
        storeContext.setAttributesForDatabase(load(instance));

        return storeContext;

    }

    private Attributes load(String name) throws Exception {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return SAXReader.parse(cl.getResource(name).toString());
    }

    private Boolean hasCause(Throwable e, Class exceptionClass) {

        log.info(e.getClass() + "-" + e.getCause());
        log.info("e.getCause().getClass().equals(exceptionClass):"
                + e.getCause().getClass().equals(exceptionClass));

        if (e.getCause() == null)
            return false;
        else if (e.getCause().getClass().equals(exceptionClass))
            return true;
        else
            return hasCause(e.getCause(), exceptionClass);
    }

    private Thread callUpdateOnThread(final String instance) {

        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    storeService.updateDB(createStoreContext(instance));
                } catch (EJBTransactionRolledbackException e) {
                    log.info(Thread.currentThread().getId()
                            + ": As expected, a EJBTransactionRolledbackException was thrown");
                    optimisticLockExceptionWasThrown[0] = hasCause(e,
                            OptimisticLockException.class);
                    semaphores.getReady().release();
                } catch (Throwable e) {
                    log.error(Thread.currentThread().getId()
                            + ":Error in a concurrent registrator thread", e);
                    semaphores.getReady().release();
                } finally {
                    log.info(Thread.currentThread().getId()
                            + ":Finished concurrent registrator thread");
                    semaphores.getReady().release();
                }
                super.run();
            }

        };

        return thread;
    }
}
