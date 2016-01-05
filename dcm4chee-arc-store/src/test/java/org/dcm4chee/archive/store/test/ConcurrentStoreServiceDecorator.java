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
 * Portions created by the Initial Developer are Copyright (C) 2011
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

import java.util.concurrent.TimeUnit;

import javax.decorator.Decorator;
import javax.decorator.Delegate;
import javax.inject.Inject;
import javax.persistence.EntityManager;

import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.archive.store.StoreContext;
import org.dcm4chee.archive.store.StoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Umberto Cappellini <umberto.cappellini@agfa.com>
 *
 */
@Decorator
public abstract class ConcurrentStoreServiceDecorator implements StoreService {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentStoreServiceDecorator.class);
    
    // injected StoreService to be decorated
    @Inject @Delegate StoreService storeService;
    
    @Inject SemaphoreHolder semaphores;
    
    @Override
    public void updateDB(StoreContext context)
            throws DicomServiceException {
        
        storeService.updateDB(context);
        
        // tell the master we're done
        semaphores.getReady().release();
        log.info("One permit for master released");

        try {
            // wait for others before committing
            semaphores.getDone().tryAcquire(1, TimeUnit.SECONDS);
            log.info("Got permit for committing");
            
        } catch (InterruptedException e) {
            log.error("InterruptedException waiting to commit");
        }     
    }

}
