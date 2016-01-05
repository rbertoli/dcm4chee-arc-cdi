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
package org.dcm4chee.archive.store.remember.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;

import org.dcm4che3.conf.api.DicomConfiguration;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.web.WebServiceAEExtension;
import org.dcm4chee.archive.conf.ArchiveAEExtension;
import org.dcm4chee.archive.conf.ArchiveDeviceExtension;
import org.dcm4chee.archive.conf.QueryParam;
import org.dcm4chee.archive.conf.QueryRetrieveView;
import org.dcm4chee.archive.dto.ArchiveInstanceLocator;
import org.dcm4chee.archive.dto.Service;
import org.dcm4chee.archive.dto.ServiceType;
import org.dcm4chee.archive.entity.StoreAndRemember;
import org.dcm4chee.archive.entity.StoreVerifyStatus;
import org.dcm4chee.archive.retrieve.RetrieveService;
import org.dcm4chee.archive.store.remember.StoreAndRememberContext;
import org.dcm4chee.archive.store.remember.StoreAndRememberContextBuilder;
import org.dcm4chee.archive.store.remember.StoreAndRememberResponse;
import org.dcm4chee.archive.store.remember.StoreAndRememberService;
import org.dcm4chee.archive.store.scu.CStoreSCUContext;
import org.dcm4chee.archive.store.verify.StoreVerifyResponse;
import org.dcm4chee.archive.store.verify.StoreVerifyResponse.VerifiedInstanceStatus;
import org.dcm4chee.archive.store.verify.StoreVerifyService;
import org.dcm4chee.archive.store.verify.StoreVerifyService.STORE_VERIFY_PROTOCOL;
import org.dcm4chee.archive.stow.client.StowContext;
import org.dcm4chee.storage.conf.Availability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Hesham Elbadawi <bsdreko@gmail.com>
 * @author Alexander Hoermandinger <alexander.hoermandinger@agfa.com>
 *
 */
@ApplicationScoped
public class StoreAndRememberServiceImpl implements StoreAndRememberService {
    private static final Logger LOG = LoggerFactory.getLogger(StoreAndRememberServiceImpl.class);

    @Inject
    private IApplicationEntityCache aeCache;
    
    @Inject
    private DicomConfiguration conf;
    
    @Inject
    private StoreVerifyService storeVerifyService;
    
    @Inject
    private RetrieveService retrieveService;
    
    @Inject
    private Device device;
    
    @Inject
    private StoreAndRememberEJB storeRememberEJB;
    
    @Inject
    private Event<StoreAndRememberResponse> storeRememberResponse;
    
    @Resource(mappedName = "java:/JmsXA")
    private ConnectionFactory connFactory;

    @Resource(mappedName = "java:/queue/storeremember")
    private Queue storeAndRememberQueue;
   
    public void storeAndRemember(StoreAndRememberContext ctx) {
        ApplicationEntity remoteAE = getRemoteAE(ctx);
        ApplicationEntity localAE = getLocalAE(ctx);
        if (localAE == null || remoteAE == null) {
            return;
        }

        List<ArchiveInstanceLocator> insts = locate(ctx.getInstances());
      
        STORE_VERIFY_PROTOCOL storeVerifyProtocol = ctx.getStoreVerifyProtocol();
        // resolve automatic configuration of store-verify protocol
        if (STORE_VERIFY_PROTOCOL.AUTO.equals(storeVerifyProtocol)) {
            WebServiceAEExtension webAEExt = remoteAE.getAEExtension(WebServiceAEExtension.class);
            if (webAEExt != null && webAEExt.getQidoRSBaseURL() != null && webAEExt.getStowRSBaseURL() != null) {
                storeVerifyProtocol = STORE_VERIFY_PROTOCOL.STOW_PLUS_QIDO;
            } else {
                storeVerifyProtocol = STORE_VERIFY_PROTOCOL.CSTORE_PLUS_STGCMT;
            }
        }
        
        boolean isDimseStoreVerify = STORE_VERIFY_PROTOCOL.CSTORE_PLUS_STGCMT.equals(storeVerifyProtocol);
        String storeVerifyTxUID = storeVerifyService.generateTransactionUID(isDimseStoreVerify);
        createOrUpdateStoreRememberTransaction(ctx, storeVerifyTxUID);
        
        switch(storeVerifyProtocol) {
        case CSTORE_PLUS_STGCMT:
            CStoreSCUContext cxt = new CStoreSCUContext(localAE, remoteAE, ServiceType.STOREREMEMBER);
            storeVerifyService.store(storeVerifyTxUID, cxt, insts);
            break;
        case STOW_PLUS_QIDO:
            WebServiceAEExtension wsExt = remoteAE.getAEExtension(WebServiceAEExtension.class);
            StowContext stowCtx = new StowContext(localAE, remoteAE, ServiceType.STOREREMEMBER);
            stowCtx.setStowRemoteBaseURL(wsExt.getStowRSBaseURL());
            stowCtx.setQidoRemoteBaseURL(wsExt.getQidoRSBaseURL());
            storeVerifyService.store(storeVerifyTxUID, stowCtx, insts);
            break;
        default:
            throw new RuntimeException("Unknown store-verify protocol " + ctx.getStoreVerifyProtocol());
        }
    }
    
    private void createOrUpdateStoreRememberTransaction(StoreAndRememberContext ctx, String storeVerifyTxUID) {
        storeRememberEJB.createOrUpdateStoreRememberTx(ctx, storeVerifyTxUID);
    }
    
    private List<ArchiveInstanceLocator> locate(String... iuids) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        Attributes keys = new Attributes();
        keys.setString(Tag.SOPInstanceUID, VR.UI, iuids);
        QueryParam queryParam = arcDev.getQueryParam();
        QueryRetrieveView view = new QueryRetrieveView();
        view.setViewID("IOCM");
        view.setHideNotRejectedInstances(false);
        queryParam.setQueryRetrieveView(view);
        return retrieveService.calculateMatches(null, keys, queryParam, false);
    }
    
    private ApplicationEntity getRemoteAE(StoreAndRememberContext cxt) {
        String extDeviceName = cxt.getExternalDeviceName();
        String remoteAeTitle = cxt.getRemoteAE();
        ApplicationEntity remoteAE = null;
        boolean error = false;
        try {
            Device extDeviceTarget = conf.findDevice(extDeviceName);
            remoteAE = extDeviceTarget.getApplicationEntity(remoteAeTitle);
        } catch(Exception e) {
           error = true;
        }
        
        if(error || remoteAE == null) {
            LOG.error(String.format("Could not resolve remote AE '%s' of external device '%s' for Store-and-Remember task", 
                    remoteAeTitle, extDeviceName));
        }
        
        return remoteAE;
    }
    
    private ApplicationEntity getLocalAE(StoreAndRememberContext ctx) {
       ApplicationEntity localAE = device.getApplicationEntity(ctx.getLocalAE());
       if(localAE == null) {
           LOG.error(String.format("Could not resolve local AE '%s' for Store-and-Remember task", ctx.getLocalAE()));
       }
       return localAE;
    }
  
    public void onStoreVerifyResponse(@Observes @Service(ServiceType.STOREREMEMBER) StoreVerifyResponse storeVerifyResponse) {
        String storeVerifyTxUID = storeVerifyResponse.getTransactionUID();
        String storeAndRememberTxUID = getStoreAndRememberTransactionUID(storeVerifyTxUID);
        if(storeAndRememberTxUID == null) {
            LOG.error("Received store verify response for non existing transaction, store-verify-transaction UID: {}", storeVerifyTxUID);
            return;
        }
        
        String localAET = storeVerifyResponse.getLocalAET();
        String remoteAET = storeVerifyResponse.getRemoteAET();
        
        Availability defaultAvailability = null;
        
        String remoteDeviceName = null;
        ArchiveAEExtension archAEExt = null;
        try {
            ApplicationEntity archiveAE = aeCache.findApplicationEntity(localAET);
            ApplicationEntity remoteAE = aeCache.findApplicationEntity(remoteAET);
            remoteDeviceName= remoteAE.getDevice().getDeviceName();
            archAEExt = archiveAE.getAEExtension(ArchiveAEExtension.class);
            defaultAvailability = archAEExt.getDefaultExternalRetrieveAETAvailability();
        } catch (ConfigurationException e) {
            //failure attempt
            LOG.error("Unable to find Application"
                    + " Entity for {} or {} verification failure for "
                    + "store and remember transaction {}",
                    localAET, remoteAET,
                    storeAndRememberTxUID);
            return;
        }
        
        String retrieveAET = remoteAET;
        Map<String, VerifiedInstanceStatus> verifiedSopInstances = storeVerifyResponse.getVerifiedInstances();
        int numToVerify = verifiedSopInstances.size();
        int numVerified = 0;
        for(Entry<String, VerifiedInstanceStatus> inst : verifiedSopInstances.entrySet()) {
            Availability externalAvailability = inst.getValue().getAvailability();
            String sopInstanceUID = inst.getKey();
            StoreAndRemember sr = storeRememberEJB.getStoreRememberTxByUIDs(storeAndRememberTxUID, sopInstanceUID);
            if (Availability.ONLINE.equals(externalAvailability) || Availability.NEARLINE.equals(externalAvailability)) {
                Availability instAvailability = (defaultAvailability == null) ? externalAvailability
                        : (externalAvailability.compareTo(defaultAvailability) <= 0 ? externalAvailability : defaultAvailability);
                
                if(sr.isRemember()) {
                    storeRememberEJB.rememberLocation(sopInstanceUID, retrieveAET, remoteDeviceName, instAvailability);
                }
                storeRememberEJB.updateStoreRemember(sr, StoreVerifyStatus.VERIFIED);
                numVerified++;
            } else {
                storeRememberEJB.updateStoreRemember(sr, StoreVerifyStatus.FAILED);
            }
        }
        
        if(numVerified < numToVerify) {
            int retriesLeft = storeRememberEJB.updatePartialStoreRemembersAndCheckForRetry(storeAndRememberTxUID);
            if(retriesLeft > 0) {
                // schedule retry
                StoreAndRememberContextBuilderImpl ctxBuilder = createContextBuilder()
                        .transactionUID(storeAndRememberTxUID)
                        .retries(retriesLeft -1);
                StoreAndRememberContext retryContext = storeRememberEJB.augmentStoreAndRememberContext(storeAndRememberTxUID, ctxBuilder)
                        .build();

                long delay = archAEExt.getStoreAndRememberDelayAfterFailedResponse() * 1000;
                
                scheduleStoreAndRemember(retryContext, delay);
            } else {
                // send 'failed' response
                storeRememberResponse.fire(new StoreAndRememberResponse(storeAndRememberTxUID, verifiedSopInstances));
            }
        } else {
            // send 'success' response
            storeRememberEJB.removeStoreRemembers(storeAndRememberTxUID);
            storeRememberResponse.fire(new StoreAndRememberResponse(storeAndRememberTxUID, verifiedSopInstances));
        }
    }
    
    private String getStoreAndRememberTransactionUID(String storeVerifyTxUID) {
        return storeRememberEJB.getStoreRememberUIDByStoreVerifyUID(storeVerifyTxUID);
    }
    
    private String generateTransactionUID() {
        return UUID.randomUUID().toString();
    }
    
    @Override
    public StoreAndRememberContextBuilderImpl createContextBuilder() {
        return new StoreAndRememberContextBuilderImpl();
    }
    
    @Override
    public void scheduleStoreAndRemember(StoreAndRememberContext ctx, long delay) {
        try {
            Connection conn = connFactory.createConnection();
            try {
                Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
                MessageProducer producer = session.createProducer(storeAndRememberQueue);
                ObjectMessage msg = session.createObjectMessage(ctx);
                StringBuilder iuid = new StringBuilder();
                for (String i : ctx.getInstances()) {
                    if (iuid.length() > 0) iuid.append(',');
                    iuid.append(i);
                }
                msg.setStringProperty("SOP_INSTANCE_UID", iuid.toString());
                if (delay > 0) {
                    msg.setLongProperty("_HQ_SCHED_DELIVERY", System.currentTimeMillis() + delay);
                }
                msg.setJMSCorrelationID(ctx.getTransactionUID());
                producer.send(msg);
            } finally {
                conn.close();
            }
        } catch (JMSException e) {
            throw new RuntimeException("Error while scheduling archiving JMS message", e);
        }
    }

    public class StoreAndRememberContextBuilderImpl implements StoreAndRememberContextBuilder {
        private StoreAndRememberContextImpl cxt = new StoreAndRememberContextImpl();
        private String[] instances;
        private String studyIUID;
        private String seriesIUID;
        private int retries = -1;
        
        @Override
        public StoreAndRememberContextBuilderImpl transactionUID(String txUID) {
            cxt.setTransactionUID(txUID);
            return this;
        }
        
        @Override
        public StoreAndRememberContextBuilderImpl instances(String... instances) {
            this.instances = instances;
            return this;
        }
        
        @Override
        public StoreAndRememberContextBuilderImpl seriesUID(String seriesIUID) {
            this.seriesIUID = seriesIUID;
            return this;
        }
        
        @Override
        public StoreAndRememberContextBuilderImpl studyUID(String studyIUID) {
            this.studyIUID = studyIUID;
            return this;
        }
        
        @Override
        public StoreAndRememberContextBuilderImpl localAE(String localAE) {
            cxt.setLocalAE(localAE);
            return this;
        }
        
        @Override
        public StoreAndRememberContextBuilderImpl externalDeviceName(String externalDeviceName) {
            cxt.setExternalDeviceName(externalDeviceName);
            return this;
        }
        
        @Override
        public StoreAndRememberContextBuilderImpl remoteAE(String remoteAE) {
            cxt.setRemoteAE(remoteAE);
            return this;
        }
        
        @Override
        public StoreAndRememberContextBuilderImpl retries(int retries) {
            this.retries = retries;
            return this;
        }
        
        @Override
        public StoreAndRememberContextBuilderImpl storeVerifyProtocol(STORE_VERIFY_PROTOCOL storeVerifyProtocol) {
            cxt.setStoreVerifyProtocol(storeVerifyProtocol);
            return this;
        }
        
        public StoreAndRememberContextBuilderImpl remember(boolean remember) {
            cxt.setRemember(remember);
            return this;
        }
        
        @Override
        public StoreAndRememberContext build() {
            String extDeviceName = cxt.getExternalDeviceName();
            if(extDeviceName == null) {
                throw new RuntimeException("Invalid store-and-remember request: no external device name set");
            }
            if(cxt.getRemoteAE() == null) {
                throw new RuntimeException("Invalid store-and-remember request: no remote AE title set");
            }
            if(cxt.getLocalAE() == null) {
                ArchiveDeviceExtension devExt = device.getDeviceExtension(ArchiveDeviceExtension.class);
                String fetchAET = devExt.getFetchAETitle();
                if (fetchAET == null) {
                    throw new IllegalStateException(
                            "Could not determine fetch AE title for Store-and-Remember");
                }
                cxt.setLocalAE(fetchAET);
            }
            if(cxt.getStoreVerifyProtocol() == null) {
                cxt.setStoreVerifyProtocol(STORE_VERIFY_PROTOCOL.AUTO);
            }
            if(cxt.isRemember() == null) {
                cxt.setRemember(true);
            }
            
            if(cxt.getTransactionUID() == null) {
                cxt.setTransactionUID(generateTransactionUID());
            }
            
            if(retries == -1) {
                ApplicationEntity ae;
                try {
                    ae = aeCache.findApplicationEntity(cxt.getLocalAE());
                } catch (ConfigurationException e) {
                    throw new RuntimeException("Could not find Application Entity for " + cxt.getLocalAE());
                }
                ArchiveAEExtension arcAE = ae.getAEExtension(ArchiveAEExtension.class);
                cxt.setRetries(arcAE.getStoreAndRememberMaxRetries());
            }
            
            if (instances == null) {
                if (seriesIUID != null) {
                    instances = storeRememberEJB.getNotExternallyArchivedSeriesInstances(seriesIUID, extDeviceName);
                } else if (studyIUID != null) {
                    instances = storeRememberEJB.getNotExternallyArchivedStudyInstances(studyIUID, extDeviceName);
                } else {
                    throw new RuntimeException("Invalid store-and-remember request: no series or study UID set");
                }
            }
            
            cxt.setInstances(instances);
            
            return cxt;
        }

    }

}
