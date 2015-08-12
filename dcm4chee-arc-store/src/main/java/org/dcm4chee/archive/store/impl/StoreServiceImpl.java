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
 * Portions created by the Initial Developer are Copyright (C) 2011-2014
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

package org.dcm4chee.archive.store.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.BulkDataDescriptor;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.io.SAXTransformer;
import org.dcm4che3.io.SAXTransformer.SetupTransformer;
import org.dcm4che3.net.*;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.DateUtils;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StreamUtils;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.archive.conf.*;
import org.dcm4chee.archive.entity.*;
import org.dcm4chee.archive.locationmgmt.LocationMgmt;
import org.dcm4chee.archive.monitoring.api.Monitored;
import org.dcm4chee.archive.patient.PatientSelectorFactory;
import org.dcm4chee.archive.patient.PatientService;
import org.dcm4chee.archive.store.StoreContext;
import org.dcm4chee.archive.store.StoreService;
import org.dcm4chee.archive.store.StoreSession;
import org.dcm4chee.archive.store.StoreSessionClosed;
import org.dcm4chee.storage.ObjectAlreadyExistsException;
import org.dcm4chee.storage.RetrieveContext;
import org.dcm4chee.storage.StorageContext;
import org.dcm4chee.storage.conf.StorageSystem;
import org.dcm4chee.storage.conf.StorageSystemGroup;
import org.dcm4chee.storage.service.RetrieveService;
import org.dcm4chee.storage.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Hesham Elbadawi <bsdreko@gmail.com>
 */
@ApplicationScoped
public class StoreServiceImpl implements StoreService {

    static Logger LOG = LoggerFactory.getLogger(StoreServiceImpl.class);

    @Inject
    private StoreServiceEJB storeServiceEJB;

    @Inject
    private StorageService storageService;

    @Inject
    private RetrieveService retrieveService;

    @Inject
    private PatientService patientService;

    @Inject
    private Event<StoreContext> storeEvent;

    @Inject
    private LocationMgmt locationManager;

    @Inject
    @StoreSessionClosed
    private Event<StoreSession> storeSessionClosed;

    @Inject
    private Device device;

    private int[] storeFilters = null;

    @Override
    public StoreSession createStoreSession(StoreService storeService) {
        return new StoreSessionImpl(storeService);
    }

    @Override
    public void initStorageSystem(StoreSession session)
            throws DicomServiceException {
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        String groupID = arcAE.getStorageSystemGroupID();
        if (groupID == null) {
            String groupType = arcAE.getStorageSystemGroupType();
            if (groupType != null) {
                StorageSystemGroup group = storageService
                        .selectBestStorageSystemGroup(groupType);
                if (group != null)
                    groupID = group.getGroupID();
            }
        }
        StorageSystem storageSystem = storageService.selectStorageSystem(
                groupID, 0);
        if (storageSystem == null)
            throw new DicomServiceException(Status.OutOfResources,
                    "No writeable Storage System in Storage System Group "
                            + groupID);
        session.setStorageSystem(storageSystem);
        StorageSystem spoolStorageSystem = null;
        String spoolGroupID = storageSystem.getStorageSystemGroup().getSpoolStorageGroup();
        if(spoolGroupID!= null){
            spoolStorageSystem= storageService.selectStorageSystem(
                    spoolGroupID, 0);
        }
        session.setSpoolStorageSystem(spoolStorageSystem != null ? 
                spoolStorageSystem : storageSystem);
    }

    @Override
    public void initMetaDataStorageSystem(StoreSession session)
            throws DicomServiceException {
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        String groupID = arcAE.getMetaDataStorageSystemGroupID();
        if (groupID != null) {
            StorageSystem storageSystem = storageService.selectStorageSystem(
                    groupID, 0);
            if (storageSystem == null)
                throw new DicomServiceException(Status.OutOfResources,
                        "No writeable Storage System in Storage System Group "
                                + groupID);
            session.setMetaDataStorageSystem(storageSystem);
        }
    }

    @Override
    public void initSpoolDirectory(StoreSession session)
            throws DicomServiceException {
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        Path spoolDir = Paths.get(arcAE.getSpoolDirectoryPath());
        if (!spoolDir.isAbsolute()) {
            StorageSystem storageSystem = session.getSpoolStorageSystem();
            spoolDir = storageService.getBaseDirectory(storageSystem).resolve(
                    spoolDir);
        }
        try {
            Files.createDirectories(spoolDir);
            Path dir = Files.createTempDirectory(spoolDir, null);
            LOG.info("{}: M-WRITE spool directory - {}", session, dir);
            session.setSpoolDirectory(dir);
        } catch (IOException e) {
            throw new DicomServiceException(Status.UnableToProcess, e);
        }
    }

    @Override
    public StoreContext createStoreContext(StoreSession session) {
        return new StoreContextImpl(session);
    }

    @Override
    public void writeSpoolFile(StoreContext context, Attributes fmi,
            InputStream data) throws DicomServiceException {
        writeSpoolFile(context, fmi, null, data);
    }

    @Override
    public void writeSpoolFile(StoreContext context, Attributes fmi,
            Attributes attrs) throws DicomServiceException {
        writeSpoolFile(context, fmi, attrs, null);
        context.setTransferSyntax(fmi.getString(Tag.TransferSyntaxUID));
        context.setAttributes(attrs);
    }

    @Override
    public void onClose(StoreSession session) {
        deleteSpoolDirectory(session);
        storeSessionClosed.fire(session);
    }

    @Override
    public void cleanup(StoreContext context) {
        if (context.getFileRef() == null) {
            deleteFinalFile(context);
            deleteMetaData(context);
        }
    }

    private void deleteMetaData(StoreContext context) {
        String storagePath = context.getMetaDataStoragePath();
        if (storagePath != null) {
            try {
                StorageSystem storageSystem = context.getStoreSession()
                        .getMetaDataStorageSystem();
                storageService.deleteObject(
                        storageService.createStorageContext(storageSystem),
                        storagePath);
            } catch (IOException e) {
                LOG.warn("{}: Failed to delete meta data - {}",
                        context.getStoreSession(), storagePath, e);
            }
        }
    }

    private void deleteFinalFile(StoreContext context) {
        String storagePath = context.getStoragePath();
        if (storagePath != null) {
            try {
                storageService.deleteObject(context.getStorageContext(),
                        storagePath);
            } catch (IOException e) {
                LOG.warn("{}: Failed to delete final file - {}",
                        context.getStoreSession(), storagePath, e);
            }
        }
    }

    private void deleteSpoolDirectory(StoreSession session) {
        Path dir = session.getSpoolDirectory();
        try (DirectoryStream<Path> directory = Files.newDirectoryStream(dir)) {
            for (Path file : directory) {
                try {
                    Files.delete(file);
                    LOG.info("{}: M-DELETE spool file - {}", session, file);
                } catch (IOException e) {
                    LOG.warn("{}: Failed to M-DELETE spool file - {}", session,
                            file, e);
                }
            }
            Files.delete(dir);
            LOG.info("{}: M-DELETE spool directory - {}", session, dir);
        } catch (IOException e) {
            LOG.warn("{}: Failed to M-DELETE spool directory - {}", session,
                    dir, e);
        }
    }

    private void writeSpoolFile(StoreContext context, Attributes fmi,
            Attributes ds, InputStream in) throws DicomServiceException {
        StoreSession session = context.getStoreSession();
        MessageDigest digest = session.getMessageDigest();
        try {
            context.setSpoolFile(spool(session, fmi, ds, in, ".dcm", digest));
            if (digest != null)
                context.setSpoolFileDigest(TagUtils.toHexString(digest.digest()));
        } catch (IOException e) {
            throw new DicomServiceException(Status.UnableToProcess, e);
        }
    }

    @Override
    public void parseSpoolFile(StoreContext context)
            throws DicomServiceException {
        Path path = context.getSpoolFile();
        try (DicomInputStream in = new DicomInputStream(path.toFile());) {
            in.setIncludeBulkData(IncludeBulkData.URI);
            Attributes fmi = in.readFileMetaInformation();
            Attributes ds = in.readDataset(-1, -1);
            context.setTransferSyntax(fmi != null ? fmi
                    .getString(Tag.TransferSyntaxUID)
                    : UID.ImplicitVRLittleEndian);
            context.setAttributes(ds);
        } catch (IOException e) {
            throw new DicomServiceException(DATA_SET_NOT_PARSEABLE);
        }
    }

    @Override
    public Path spool(StoreSession session, InputStream in, String suffix)
            throws IOException {
        return spool(session, null, null, in, suffix, null);
    }

    private Path spool(StoreSession session, Attributes fmi, Attributes ds,
            InputStream in, String suffix, MessageDigest digest)
            throws IOException {
        Path spoolDirectory = session.getSpoolDirectory();
        Path path = Files.createTempFile(spoolDirectory, null, suffix);
        OutputStream out = Files.newOutputStream(path);
        try {
            if (digest != null) {
                digest.reset();
                out = new DigestOutputStream(out, digest);
            }
            out = new BufferedOutputStream(out);
            if (fmi != null) {
                @SuppressWarnings("resource")
                DicomOutputStream dout = new DicomOutputStream(out,
                        UID.ExplicitVRLittleEndian);
                if (ds == null)
                    dout.writeFileMetaInformation(fmi);
                else
                    dout.writeDataset(fmi, ds);
                out = dout;
            }
            if (in instanceof PDVInputStream) {
                ((PDVInputStream) in).copyTo(out);
            } else if (in != null) {
                StreamUtils.copy(in, out);
            }
        } finally {
            SafeClose.close(out);
        }
        LOG.info("{}: M-WRITE spool file - {}", session, path);
        return path;
    }

    @Override
    public void store(StoreContext context) throws DicomServiceException {
        StoreSession session = context.getStoreSession();
        StoreService service = session.getStoreService();
        try {
            service.storeMetaData(context);
            service.processFile(context);
            service.coerceAttributes(context);
            service.updateDB(context);
        } catch (DicomServiceException e) {
            context.setStoreAction(StoreAction.FAIL);
            context.setThrowable(e);
            throw e;
        } finally {
            service.fireStoreEvent(context);
            service.cleanup(context);
        }
    }

    @Override
    public void fireStoreEvent(StoreContext context) {
        storeEvent.fire(context);
    }

    /*
     * coerceAttributes applies a loaded XSL stylesheet on the keys if given
     * currently 15/4/2014 modifies date and time attributes in the keys per
     * request
     */
    @Override
    public void coerceAttributes(final StoreContext context)
            throws DicomServiceException {

        final StoreSession session = context.getStoreSession();
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        Attributes attrs = context.getAttributes();
        try {
            Attributes modified = context.getCoercedOriginalAttributes();
            Templates tpl = session.getRemoteAET() != null ? arcAE
                    .getAttributeCoercionTemplates(
                            attrs.getString(Tag.SOPClassUID), Dimse.C_STORE_RQ,
                            TransferCapability.Role.SCP, session.getRemoteAET())
                    : null;
            if (tpl != null) {
                attrs.update(SAXTransformer.transform(attrs, tpl, false, false,
                        new SetupTransformer() {

                            @Override
                            public void setup(Transformer transformer) {
                                setParameters(transformer, session);
                            }
                        }), modified);
            }
        } catch (Exception e) {
            throw new DicomServiceException(Status.UnableToProcess, e);
        }
        // store service time zone support moved to decorator

    }

    private void setParameters(Transformer tr, StoreSession session) {
        Date date = new Date();
        String currentDate = DateUtils.formatDA(null, date);
        String currentTime = DateUtils.formatTM(null, date);
        tr.setParameter("date", currentDate);
        tr.setParameter("time", currentTime);
        tr.setParameter("calling", session.getRemoteAET());
        tr.setParameter("called", session.getLocalAET());
    }

    @Override
    @Monitored(name="processFile")
    public void processFile(StoreContext context) throws DicomServiceException {
        try {
            StoreSession session = context.getStoreSession();
            StorageContext storageContext = storageService
                    .createStorageContext(session.getStorageSystem());
            Path source = context.getSpoolFile();
            context.setStorageContext(storageContext);
            context.setFinalFileDigest(context.getSpoolFileDigest());
            context.setFinalFileSize(Files.size(source));

            String origStoragePath = context.calcStoragePath();
            String storagePath = origStoragePath;
            int copies = 1;
            for (;;) {
                try {
                    storageService
                            .moveFile(storageContext, source, storagePath);
                    context.setStoragePath(storagePath);
                    return;
                } catch (ObjectAlreadyExistsException e) {
                    storagePath = origStoragePath + '.' + copies++;
                }
            }
        } catch (Exception e) {
            throw new DicomServiceException(Status.UnableToProcess, e);
        }
    }

    @Override
    public void updateDB(StoreContext context) throws DicomServiceException {

        ArchiveDeviceExtension dE = context.getStoreSession().getDevice()
                .getDeviceExtension(ArchiveDeviceExtension.class);

        try {
            String nodbAttrsDigest = noDBAttsDigest(context.getStoragePath(),
                    context.getStoreSession());
            context.setNoDBAttsDigest(nodbAttrsDigest);
        } catch (IOException e1) {
            throw new DicomServiceException(Status.UnableToProcess, e1);
        }

        for (int i = 0; i <= dE.getUpdateDbRetries(); i++) {

            try {
                LOG.info("{}: try to updateDB, try nr. {}",
                        context.getStoreSession(), i);
                storeServiceEJB.updateDB(context);
                break;
            } catch (RuntimeException e) {
                if (i >= dE.getUpdateDbRetries()) // last try failed
                    throw new DicomServiceException(Status.UnableToProcess, e);
                else
                    LOG.warn("{}: Failed to updateDB, try nr. {}",
                            context.getStoreSession(), i, e);
            }
        }

        updateAttributes(context);
    }

    private void updateAttributes(StoreContext context) {
        Instance instance = context.getInstance();
        Series series = instance.getSeries();
        Study study = series.getStudy();
        Patient patient = study.getPatient();
        Attributes attrs = context.getAttributes();
        Attributes modified = new Attributes();
        attrs.update(patient.getAttributes(), modified);
        attrs.update(study.getAttributes(), modified);
        attrs.update(series.getAttributes(), modified);
        attrs.update(instance.getAttributes(), modified);
        if (!modified.isEmpty()) {
            modified.addAll(context.getCoercedOriginalAttributes());
            context.setCoercedOrginalAttributes(modified);
        }
        logCoercedAttributes(context);
    }

    private void logCoercedAttributes(StoreContext context) {
        StoreSession session = context.getStoreSession();
        Attributes attrs = context.getCoercedOriginalAttributes();
        if (!attrs.isEmpty()) {
            LOG.info("{}: Coerced Attributes:\n{}New Attributes:\n{}", session,
                    attrs,
                    new Attributes(context.getAttributes(), attrs.tags()));
        }
    }

    @Override
    public StoreAction instanceExists(EntityManager em, StoreContext context,
            Instance instance) throws DicomServiceException {
        StoreSession session = context.getStoreSession();

        Collection<Location> fileRefs = instance.getLocations();

        if (fileRefs.isEmpty())
            return StoreAction.RESTORE;

        if (context.getStoreSession().getArchiveAEExtension()
                .isIgnoreDuplicatesOnStorage())
            return StoreAction.IGNORE;

        if (!hasSameSourceAET(instance, session.getRemoteAET()))
            return StoreAction.IGNORE;

        if (hasFileRefWithDigest(fileRefs, context.getSpoolFileDigest()))
            return StoreAction.IGNORE;

        if (context.getStoreSession().getArchiveAEExtension()
                .isCheckNonDBAttributesOnStorage()
                && (hasFileRefWithOtherAttsDigest(fileRefs,
                        context.getNoDBAttsDigest())))
            return StoreAction.UPDATEDB;

        return StoreAction.REPLACE;
    }

    private boolean hasSameSourceAET(Instance instance, String remoteAET) {
        return remoteAET.equals(instance.getSeries().getSourceAET());
    }

    private boolean hasFileRefWithDigest(Collection<Location> fileRefs,
            String digest) {
        if (digest == null)
            return false;

        for (Location fileRef : fileRefs) {
            if (digest.equals(fileRef.getDigest()))
                return true;
        }
        return false;
    }

    private boolean hasFileRefWithOtherAttsDigest(
            Collection<Location> fileRefs, String digest) {
        if (digest == null)
            return false;

        for (Location fileRef : fileRefs) {
            if (digest.equals(fileRef.getOtherAttsDigest()))
                return true;
        }
        return false;
    }

    @Override
    public Instance findOrCreateInstance(EntityManager em, StoreContext context)
            throws DicomServiceException {
        StoreSession session = context.getStoreSession();
        StoreService service = session.getStoreService();
        Collection<Location> replaced = new ArrayList<>();

        try {

            Attributes attrs = context.getAttributes();
            Instance inst = em
                    .createNamedQuery(Instance.FIND_BY_SOP_INSTANCE_UID_EAGER,
                            Instance.class)
                    .setParameter(1, attrs.getString(Tag.SOPInstanceUID))
                    .getSingleResult();
            StoreAction action = service.instanceExists(em, context, inst);
            LOG.info("{}: {} already exists - {}", session, inst, action);
            context.setStoreAction(action);
            switch (action) {
            case RESTORE:
            case UPDATEDB:
                storeServiceEJB.updateInstance(context, inst);
            case IGNORE:
                unmarkLocationsForDelete(inst, context);
                return inst;
            case REPLACE:
                for (Iterator<Location> iter = inst.getLocations().iterator(); iter
                        .hasNext();) {
                    Location fileRef = iter.next();
                    // no other instances referenced through alias table
                    if (fileRef.getInstances().size() == 1) {
                        // delete
                        replaced.add(fileRef);
                    } else {
                        // remove inst
                        fileRef.getInstances().remove(inst);
                    }
                    iter.remove();
                }
                em.remove(inst);
            }
        } catch (NoResultException e) {
            context.setStoreAction(StoreAction.STORE);
        } catch (DicomServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DicomServiceException(Status.UnableToProcess, e);
        }

        Instance newInst = storeServiceEJB.createInstance(context);

        // delete replaced
        try {
            if (replaced.size()>0)
                locationManager.scheduleDelete(replaced, 0,false);
        } catch (Exception e) {
            LOG.error("StoreService : Error deleting replaced location - {}", e);
        }
        return newInst;
    }

    @Override
    public Series findOrCreateSeries(EntityManager em, StoreContext context)
            throws DicomServiceException {
        Attributes attrs = context.getAttributes();
        try {
            Series series = em
                    .createNamedQuery(Series.FIND_BY_SERIES_INSTANCE_UID_EAGER,
                            Series.class)
                    .setParameter(1, attrs.getString(Tag.SeriesInstanceUID))
                    .getSingleResult();
            storeServiceEJB.updateSeries(context, series);
            return series;
        } catch (NoResultException e) {
            return storeServiceEJB.createSeries(context);
        } catch (Exception e) {
            throw new DicomServiceException(Status.UnableToProcess, e);
        }
    }

    @Override
    public Study findOrCreateStudy(EntityManager em, StoreContext context)
            throws DicomServiceException {
        Attributes attrs = context.getAttributes();
        try {
            Study study = em
                    .createNamedQuery(Study.FIND_BY_STUDY_INSTANCE_UID_EAGER,
                            Study.class)
                    .setParameter(1, attrs.getString(Tag.StudyInstanceUID))
                    .getSingleResult();
            storeServiceEJB.updateStudy(context, study);
            return study;
        } catch (NoResultException e) {
            return storeServiceEJB.createStudy(context);
        } catch (Exception e) {
            throw new DicomServiceException(Status.UnableToProcess, e);
        }
    }

    @Override
    public Patient findOrCreatePatient(EntityManager em, StoreContext context)
            throws DicomServiceException {
        try {
            // ArchiveAEExtension arcAE = context.getStoreSession()
            // .getArchiveAEExtension();
            // PatientSelector selector = arcAE.getPatientSelector();
            // System.out.println("Selector Class Name:"+selector.getPatientSelectorClassName());
            // for (String key :
            // selector.getPatientSelectorProperties().keySet())
            // System.out.println("Property:("+key+","+selector.getPatientSelectorProperties().get(key)+")");

            StoreSession session = context.getStoreSession();
            return patientService.updateOrCreatePatientOnCStore(context
                    .getAttributes(), PatientSelectorFactory
                    .createSelector(session.getStoreParam()),
                    session.getStoreParam());
        } catch (Exception e) {
            throw new DicomServiceException(Status.UnableToProcess, e);
        }
    }

    private int[] getStoreFilters(Attributes attrs) {

        if (storeFilters == null) {

            ArchiveDeviceExtension dExt = device
                    .getDeviceExtension(ArchiveDeviceExtension.class);
            storeFilters = merge(
                    dExt.getAttributeFilter(Entity.Patient)
                            .getCompleteSelection(attrs),
                    dExt.getAttributeFilter(Entity.Study).getCompleteSelection(
                            attrs), dExt.getAttributeFilter(Entity.Series)
                            .getCompleteSelection(attrs), dExt
                            .getAttributeFilter(Entity.Instance)
                            .getCompleteSelection(attrs));
            Arrays.sort(storeFilters);
        }

        return storeFilters;
    }

    @Override
    public void storeMetaData(StoreContext context)
            throws DicomServiceException {
        StoreSession session = context.getStoreSession();
        StorageSystem storageSystem = session.getMetaDataStorageSystem();
        if (storageSystem == null)
            return;

        try {
            StorageContext storageContext = storageService
                    .createStorageContext(storageSystem);
            String origStoragePath = context.calcMetaDataStoragePath();
            String storagePath = origStoragePath;
            int copies = 1;
            for (;;) {
                try {
                    try (DicomOutputStream out = new DicomOutputStream(
                            storageService.openOutputStream(storageContext,
                                    storagePath), UID.ExplicitVRLittleEndian)) {
                        storeMetaDataTo(context.getAttributes(), out);
                    }
                    context.setMetaDataStoragePath(storagePath);
                    return;
                } catch (ObjectAlreadyExistsException e) {
                    storagePath = origStoragePath + '.' + copies++;
                }
            }
        } catch (Exception e) {
            throw new DicomServiceException(Status.UnableToProcess, e);
        }
    }

    private void storeMetaDataTo(Attributes attrs, DicomOutputStream out)
            throws IOException {
        Attributes metaData = new Attributes(attrs.bigEndian(), attrs.size());
        metaData.addWithoutBulkData(attrs, BulkDataDescriptor.DEFAULT);
        out.writeDataset(
                metaData.createFileMetaInformation(UID.ExplicitVRLittleEndian),
                metaData);
    }

    public int[] merge(final int[]... arrays) {
        int size = 0;
        for (int[] a : arrays)
            size += a.length;

        int[] res = new int[size];

        int destPos = 0;
        for (int i = 0; i < arrays.length; i++) {
            if (i > 0)
                destPos += arrays[i - 1].length;
            int length = arrays[i].length;
            System.arraycopy(arrays[i], 0, res, destPos, length);
        }

        return res;
    }

    private void unmarkLocationsForDelete(Instance inst, StoreContext context) {
        for (Location loc : inst.getLocations()) {
            if (loc.getStatus() == Location.Status.DELETE_FAILED) {
                if (loc.getStorageSystemGroupID().compareTo(
                        context.getStoreSession().getArchiveAEExtension()
                                .getStorageSystemGroupID()) == 0) 
                    loc.setStatus(Location.Status.OK);
                else if(belongsToAnyOnline(loc))
                    loc.setStatus(Location.Status.OK);
                else
                    loc.setStatus(Location.Status.ARCHIVE_FAILED);
            }
        }
    }

    private boolean belongsToAnyOnline(Location loc) {
        for (ApplicationEntity ae : device.getApplicationEntities()) {
            ArchiveAEExtension arcAEExt = ae
                    .getAEExtension(ArchiveAEExtension.class);
            if (arcAEExt.getStorageSystemGroupID().compareTo(
                    loc.getStorageSystemGroupID()) == 0)
                return true;
        }
        return false;
    }

    /**
     * Given a reference to a stored object, retrieves it and calculates the
     * digest of all the attributes (including bulk data), not stored in the
     * database. This step is optionally skipped by configuration.
     */
    private String noDBAttsDigest(String path, StoreSession session)
            throws IOException {

        if (session.getArchiveAEExtension().isCheckNonDBAttributesOnStorage()) {

            // retrieves and parses the object
            RetrieveContext retrieveContext = retrieveService
                    .createRetrieveContext(session.getStorageSystem());
            InputStream stream = retrieveService.openInputStream(
                    retrieveContext, path);
            DicomInputStream dstream = new DicomInputStream(stream);
            dstream.setIncludeBulkData(IncludeBulkData.URI);
            Attributes attrs = dstream.readDataset(-1, -1);
            dstream.close();

            // selects attributes non stored in the db
            Attributes noDBAtts = new Attributes();
            noDBAtts.addNotSelected(attrs, getStoreFilters(attrs));

            return Utils.digestAttributes(noDBAtts, session.getMessageDigest());
        } else
            return null;
    }

}
