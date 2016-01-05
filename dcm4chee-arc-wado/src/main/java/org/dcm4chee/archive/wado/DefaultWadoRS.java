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
package org.dcm4chee.archive.wado;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;

import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.DatasetWithFMI;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.codec.ImageReaderFactory;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.TransferCapability.Role;
import org.dcm4che3.net.service.BasicCStoreSCUResp;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.InstanceLocator;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.archive.dto.ArchiveInstanceLocator;
import org.dcm4chee.archive.dto.GenericParticipant;
import org.dcm4chee.archive.dto.ServiceType;
import org.dcm4chee.archive.fetch.forward.FetchForwardCallBack;
import org.dcm4chee.archive.fetch.forward.FetchForwardService;
import org.dcm4chee.archive.retrieve.impl.RetrieveAfterSendEvent;
import org.dcm4chee.archive.rs.HostAECache;
import org.dcm4chee.archive.rs.HttpSource;
import org.dcm4chee.archive.store.scu.CStoreSCUContext;
import org.dcm4chee.archive.web.WadoRS;
import org.dcm4chee.storage.conf.StorageDeviceExtension;
import org.dcm4chee.storage.conf.StorageSystemGroup;
import org.dcm4chee.task.WeightWatcher;
import org.jboss.resteasy.plugins.providers.multipart.ContentIDUtils;
import org.jboss.resteasy.plugins.providers.multipart.MultipartRelatedOutput;
import org.jboss.resteasy.plugins.providers.multipart.OutputPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Umberto Cappellini <umberto.cappellini@agfa.com>
 * @author Hesham Elbadawi <bsdreko@gmail.com>
 * @author Hermann Czedik-Eysenberg <hermann-agfa@czedik.net>
 * @author Alessio Roselli <alessio.roselli@agfa.com>
 */
public class DefaultWadoRS extends Wado implements WadoRS {

    @Inject
    private Event<RetrieveAfterSendEvent> retrieveEvent;

    @Inject
    private HostAECache hostAECache;

    @Inject
    private FetchForwardService fetchForwardService;

    @Inject
    private WeightWatcher weightWatcher;

    private static final int STATUS_OK = 200;
    private static final int STATUS_PARTIAL_CONTENT = 206;
    private static final int STATUS_NOT_ACCEPTABLE = 406;
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_ID = "Content-ID";
    private static final String CONTENT_LOCATION = "Content-Location";

    private static final Logger LOG = LoggerFactory.getLogger(DefaultWadoRS.class);

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Context
    private HttpHeaders headers;

    private CStoreSCUContext context;

    private boolean acceptAll;

    private boolean acceptZip;

    private boolean acceptDicomXML;

    private boolean acceptDicomJSON;

    private boolean acceptDicom;

    private boolean acceptOctetStream; // Little Endian uncompressed bulk data

    private boolean acceptBulkdata;

    private List<String> acceptedTransferSyntaxes;

    private List<MediaType> acceptedBulkdataMediaTypes;

    private String method;

    private String toBulkDataURI(String uri) {
        return uriInfo.getBaseUri() + aetitle + "/bulkdata/"
                + URI.create(uri).getPath();
    }

    private void init(String method) {

        ApplicationEntity sourceAE;
        try {
            sourceAE = hostAECache.findAE(new HttpSource(request));
        } catch (ConfigurationException e) {
            throw new WebApplicationException(e, Status.INTERNAL_SERVER_ERROR);
        }

        context = new CStoreSCUContext(arcAE.getApplicationEntity(), sourceAE, ServiceType.WADOSERVICE);

        this.method = method;
        List<MediaType> acceptableMediaTypes = headers
                .getAcceptableMediaTypes();
        ApplicationEntity ae = device.getApplicationEntity(aetitle);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(Status.SERVICE_UNAVAILABLE);

        this.acceptedTransferSyntaxes = new ArrayList<String>(
                acceptableMediaTypes.size());

        this.acceptedBulkdataMediaTypes = new ArrayList<MediaType>(
                acceptableMediaTypes.size());

        for (MediaType mediaType : acceptableMediaTypes) {
            if (mediaType.isWildcardType())
                acceptAll = true;
            else if (mediaType.isCompatible(MediaTypes.APPLICATION_ZIP_TYPE))
                acceptZip = true;
            else if (mediaType.isCompatible(MediaTypes.MULTIPART_RELATED_TYPE)) {
                try {
                    MediaType relatedType = MediaType.valueOf(mediaType
                            .getParameters().get("type"));
                    if (relatedType
                            .isCompatible(MediaTypes.APPLICATION_DICOM_TYPE)) {
                        acceptDicom = true;
                        String acceptedTsUID = mediaType.getParameters().get("transfer-syntax");
                        // note: acceptedTsUID could be null, then it means that any transfer syntax is accepted
                        acceptedTransferSyntaxes.add(acceptedTsUID);
                    } else if (relatedType
                            .isCompatible(MediaTypes.APPLICATION_DICOM_XML_TYPE)) {
                        acceptDicomXML = true;
                    } else {
                        acceptBulkdata = true;
                        if (relatedType.isCompatible(MediaType.APPLICATION_OCTET_STREAM_TYPE)) {
                            acceptOctetStream = true;
                        } else {
                            String acceptedTsUID = mediaType.getParameters().get("transfer-syntax");
                            if (acceptedTsUID != null)
                                relatedType = MediaType.valueOf(relatedType.toString() + ";transfer-syntax=" + acceptedTsUID);
                        }
                        acceptedBulkdataMediaTypes.add(relatedType);
                    }
                } catch (IllegalArgumentException e) {
                    throw new WebApplicationException(Status.BAD_REQUEST);
                }
            } else if (headers.getAcceptableMediaTypes().contains(
                    MediaType.APPLICATION_JSON_TYPE)) {
                acceptDicomJSON = true;
            }
        }
    }

    private String selectDicomTransferSyntaxes(ArchiveInstanceLocator ref, DatasetWithFMI datasetWithFMI) {

        if (context.getLocalAE() != null) {
            if (arcAE.getRetrieveSuppressionCriteria().isCheckTransferCapabilities()) {
                if (confSupportsTransferSyntax(ref) &&
                        !ref.getStorageSystem().getStorageSystemGroup().isPossiblyFaultyJPEGLS(datasetWithFMI))
                    return ref.tsuid;
                else
                    return getDefaultConfiguredTransferSyntax(ref);
            }
        }

        // prevent that (possibly) faulty JPEG-LS data leaves the system,
        // we only want to send it decompressed
        if (!ref.getStorageSystem().getStorageSystemGroup().isPossiblyFaultyJPEGLS(datasetWithFMI)) {

            for (String ts1 : acceptedTransferSyntaxes) {
                if (ts1 == null || ts1.equals(ref.tsuid))
                    return ref.tsuid;
            }

        }

        if (ImageReaderFactory.canDecompress(ref.tsuid)) {
            // note: null means any transfer syntax is accepted
            if (acceptedTransferSyntaxes.contains(null) || acceptedTransferSyntaxes.contains(UID.ExplicitVRLittleEndian)) {
                return UID.ExplicitVRLittleEndian;
            } else if (acceptedTransferSyntaxes.contains(UID.ImplicitVRLittleEndian)) {
                return UID.ImplicitVRLittleEndian;
            }
        }

        return null;
    }

    private boolean confSupportsTransferSyntax(InstanceLocator ref) {
        Collection<TransferCapability> aeTCs = context.getLocalAE().getTransferCapabilitiesWithRole(Role.SCU);
        for (TransferCapability supportedTC : aeTCs) {
            if (ref.cuid.equals(supportedTC.getSopClass()) &&
                    supportedTC.containsTransferSyntax(ref.tsuid)) {
                return true;
            }
        }
        return false;
    }

    private String getDefaultConfiguredTransferSyntax(InstanceLocator ref) {
        Collection<TransferCapability> aeTCs = context.getLocalAE().getTransferCapabilitiesWithRole(Role.SCU);
        for (TransferCapability supportedTC : aeTCs){
            if (ref.cuid.equals(supportedTC.getSopClass())) {
                if (supportedTC.containsTransferSyntax(UID.ExplicitVRLittleEndian))
                    return UID.ExplicitVRLittleEndian;
                else
                    return UID.ImplicitVRLittleEndian;
            }
        }
        return UID.ImplicitVRLittleEndian;
    }

    private MediaType selectBulkdataMediaTypeForTransferSyntax(StorageSystemGroup storageSystemGroup, DatasetWithFMI datasetWithFMI, String ts) {

        // prevent that faulty JPEG-LS data leaves the system
        boolean rejectCompressed = storageSystemGroup.isPossiblyFaultyJPEGLS(datasetWithFMI);

        if (!rejectCompressed) {
            MediaType requiredMediaType = null;
            try {
                requiredMediaType = MediaTypes.forTransferSyntax(ts);
            } catch (IllegalArgumentException e) {
                // ignored
            }
            if (requiredMediaType == null)
                return null;

            if (acceptAll)
                return requiredMediaType;

            String requiredTransferSyntaxUID = MediaTypes.transferSyntaxOf(requiredMediaType);

            for (MediaType mediaType : acceptedBulkdataMediaTypes) {
                if (mediaType.isCompatible(requiredMediaType) &&
                        requiredTransferSyntaxUID.equals(MediaTypes.transferSyntaxOf(mediaType))) {
                    return requiredMediaType;
                }
            }
        }

        if (acceptOctetStream && ImageReaderFactory.canDecompress(ts)) {
            return MediaType.APPLICATION_OCTET_STREAM_TYPE;
        }

        return null;
    }

    @Override
    public Response retrieveStudy(String studyInstanceUID)
            throws DicomServiceException  {
        init("retrieveStudy");

        List<ArchiveInstanceLocator> instances = retrieveService
                .calculateMatches(studyInstanceUID, null, null, queryParam, false);

        return retrieve(instances);
    }

    @Override
    public Response retrieveSeries(String studyInstanceUID,String seriesInstanceUID)
            throws DicomServiceException {
        init("retrieveSeries");

        List<ArchiveInstanceLocator> instances = retrieveService
                .calculateMatches(studyInstanceUID, seriesInstanceUID, null,
                        queryParam, false);

        return retrieve(instances);
    }

    @Override
    public Response retrieveInstance(String studyInstanceUID,String seriesInstanceUID,String sopInstanceUID)
            throws DicomServiceException {
        init("retrieveInstance");

        List<ArchiveInstanceLocator> instances = retrieveService
                .calculateMatches(studyInstanceUID, seriesInstanceUID,
                        sopInstanceUID, queryParam, false);

        return retrieve(instances);
    }

    @Override
    public Response retrieveFrame(String studyInstanceUID,String seriesInstanceUID,String sopInstanceUID,FrameList frameList) {
        init("retrieveFrame");

        List<ArchiveInstanceLocator> instances = retrieveService
                .calculateMatches(studyInstanceUID, seriesInstanceUID,
                        sopInstanceUID, queryParam, false);

        if (instances == null || instances.size() == 0)
            throw new WebApplicationException(Status.NOT_FOUND);

        return retrievePixelData(instances.get(0), frameList.getFrames());
    }

    /**
     * BulkDataURI is expected to be a path to a file.
     */
    @Override
    public Response retrieveBulkdata(String storageSystemGroupName,String storageSystemName,String bulkDataPath,int offset,int length) {

        init("retrieveBulkdata");

        String bulkDataURI = "file://" + bulkDataPath;

        if (length <= 0) {
            // compressed pixel data

            StorageDeviceExtension storageDeviceExtension = device.getDeviceExtension(StorageDeviceExtension.class);
            StorageSystemGroup storageSystemGroup = storageDeviceExtension.getStorageSystemGroup(storageSystemGroupName);
            Objects.requireNonNull(storageSystemGroup);

            return retrievePixelDataFromFile(storageSystemGroup, bulkDataURI);
        } else {
            // uncompressed pixel data, or some other bulk data (e.g. overlay data)

            return retrieveBulkData(new BulkData(bulkDataURI, offset, length, false));
        }
    }

    @Override
    public Response retrieveStudyMetadata(String studyInstanceUID)
            throws DicomServiceException {
        init("retrieveMetadata");

        List<ArchiveInstanceLocator> instances = retrieveService
                .calculateMatches(studyInstanceUID, null, null, queryParam, false);

        return retrieveMetadata(instances);
    }

    // create metadata retrieval for Series
    @Override
    public Response retrieveSeriesMetadata(String studyInstanceUID,String seriesInstanceUID)
            throws DicomServiceException {
        init("retrieveMetadata");

        List<ArchiveInstanceLocator> instances = retrieveService
                .calculateMatches(studyInstanceUID, seriesInstanceUID, null,
                        queryParam, false);

        return retrieveMetadata(instances);
    }

    // create metadata retrieval for Instances
    @Override
    public Response retrieveInstanceMetadata(String studyInstanceUID,String seriesInstanceUID,String sopInstanceUID)
            throws DicomServiceException {
        init("retrieveMetadata");

        List<ArchiveInstanceLocator> instances = retrieveService
                .calculateMatches(studyInstanceUID, seriesInstanceUID,
                        sopInstanceUID, queryParam, false);

        return retrieveMetadata(instances);
    }

    public static void replacePixelDataBulkDataURI(ArchiveInstanceLocator ref, Attributes dataset) {
        Object pixelData = dataset.getValue(Tag.PixelData);

        // HACK: in the case of compressed pixel data we are replacing the bulk data uri by one with undefined length

        if (pixelData instanceof Fragments) {
            Fragments frags = (Fragments) pixelData;
            String pixeldataBulkDataURI = ((BulkData) frags.get(1))
                    .uriWithoutQuery() +
                    "?offset=0" +
                    "&length=-1" +
                    "&storageSystemGroup=" + ref.getStorageSystem().getStorageSystemGroup().getGroupID() +
                    "&storageSystem=" + ref.getStorageSystem().getStorageSystemID();
            dataset.setValue(
                    Tag.PixelData,
                    VR.OB,
                    new BulkData(null, pixeldataBulkDataURI,
                            dataset.bigEndian()));
        }
    }

    private Response retrieve(List<ArchiveInstanceLocator> refs)
            throws DicomServiceException {

        List<ArchiveInstanceLocator> insts = new ArrayList<ArchiveInstanceLocator>();
        List<ArchiveInstanceLocator> instswarning = new ArrayList<ArchiveInstanceLocator>();
        final List<ArchiveInstanceLocator> instscompleted = new ArrayList<ArchiveInstanceLocator>();
        final List<ArchiveInstanceLocator> instsfailed = new ArrayList<ArchiveInstanceLocator>();

        try {
            if (refs.isEmpty())
                throw new WebApplicationException(Status.NOT_FOUND);
            else {
                insts.addAll(refs);
            }

            refs = eliminateSuppressedSOPClasses(refs);

            refs = eliminateSuppressedInstances(refs);

            ArrayList<ArchiveInstanceLocator> external = extractExternalLocators(refs);
            final MultipartRelatedOutput multiPartOutput = new MultipartRelatedOutput();
            final ZipOutput zipOutput = new ZipOutput();
            if (!refs.isEmpty()) {
                addDicomOrBulkDataOrZip(refs, instscompleted, instsfailed,
                        multiPartOutput, zipOutput);
            }
            if (!external.isEmpty()) {
                FetchForwardCallBack fetchCallBack = new FetchForwardCallBack() {
                    @Override
                    public void onFetch(Collection<ArchiveInstanceLocator> instances,
                                        BasicCStoreSCUResp resp) {
                        addDicomOrBulkDataOrZip((List<ArchiveInstanceLocator>) instances, instscompleted, instsfailed,
                                multiPartOutput, zipOutput);
                    }
                };

                ArrayList<ArchiveInstanceLocator> failedToFetchForward = new ArrayList<ArchiveInstanceLocator>();
                failedToFetchForward = fetchForwardService.fetchForward(aetitle, external, fetchCallBack, fetchCallBack);
                instsfailed.addAll(failedToFetchForward);
            }
            if (!acceptDicom && !acceptBulkdata && (acceptAll || acceptZip)) {
                return Response.ok().entity(zipOutput)
                        .type(MediaTypes.APPLICATION_ZIP_TYPE).build();
            } else {
                int status = instsfailed.size() > 0 ? STATUS_PARTIAL_CONTENT : STATUS_OK;
                return Response.status(status).entity(multiPartOutput).build();
            }
        } finally {
            // audit
            retrieveEvent.fire(new RetrieveAfterSendEvent(
                    new GenericParticipant(request.getRemoteAddr(), request
                            .getRemoteUser()), new GenericParticipant(request
                            .getLocalAddr(), null), new GenericParticipant(
                            request.getRemoteAddr(), request.getRemoteUser()),
                    device, insts, instscompleted, instswarning, instsfailed));
        }
    }

    private List<ArchiveInstanceLocator> eliminateSuppressedSOPClasses(List<ArchiveInstanceLocator> refs) {
        // check for SOP classes elimination
        if (arcAE.getRetrieveSuppressionCriteria().isCheckTransferCapabilities()) {
            List<ArchiveInstanceLocator> adjustedRefs = new ArrayList<>();
            for (ArchiveInstanceLocator ref : refs) {
                if (!storescuService.isSOPClassSuppressed(ref, context)) {
                    adjustedRefs.add(ref);
                }
            }
            refs = adjustedRefs;
        }
        return refs;
    }

    private List<ArchiveInstanceLocator> eliminateSuppressedInstances(List<ArchiveInstanceLocator> refs) {
        // check for suppression criteria
        if (context.getRemoteAE() != null) {
            Map<String, String> suppressionCriteriaMap = arcAE.getRetrieveSuppressionCriteria().getSuppressionCriteriaMap();
            String supressionCriteriaTemplateURI = suppressionCriteriaMap.get(context.getRemoteAE().getAETitle());
            if (supressionCriteriaTemplateURI != null) {
                List<ArchiveInstanceLocator> adjustedRefs = new ArrayList<>();
                for (ArchiveInstanceLocator ref : refs) {
                    Attributes attrs = getFileAttributes(ref);
                    if (!storescuService.isInstanceSuppressed(ref, attrs, supressionCriteriaTemplateURI, context)) {
                        adjustedRefs.add(ref);
                    }
                }
                return adjustedRefs;
            }
        }
        return refs;
    }

    private void addDicomOrBulkDataOrZip(List<ArchiveInstanceLocator> refs,
            List<ArchiveInstanceLocator> instscompleted,
            List<ArchiveInstanceLocator> instsfailed,
            MultipartRelatedOutput multiPartOutput, ZipOutput zipOutput) {
        if (acceptDicom || acceptBulkdata) {
            retrieveDicomOrBulkData(refs, instscompleted,
                    instsfailed, multiPartOutput);
        } else {
            if (!acceptZip && !acceptAll)
                throw new WebApplicationException(Status.NOT_ACCEPTABLE);
            retrieveZIP(refs, instsfailed, instscompleted, zipOutput);
        }
    }

    private void retrieveDicomOrBulkData(List<ArchiveInstanceLocator> refs,
            List<ArchiveInstanceLocator> instscompleted,
            List<ArchiveInstanceLocator> instsfailed,
            MultipartRelatedOutput output) {
        if (acceptedBulkdataMediaTypes.isEmpty()) {
            for (ArchiveInstanceLocator ref : refs) {
                if (!addDicomObjectTo(ref, output)) {
                    instsfailed.add((ArchiveInstanceLocator) ref);
                } else
                    instscompleted.add((ArchiveInstanceLocator) ref);
            }
        } else {
            for (ArchiveInstanceLocator ref : refs) {
                if (addPixelDataTo(ref.getStorageSystem().getStorageSystemGroup(), ref.uri, output) != STATUS_OK) {
                    instsfailed.add((ArchiveInstanceLocator) ref);
                } else {
                    instscompleted.add((ArchiveInstanceLocator) ref);
                }
            }
        }

        if (output.getParts().isEmpty())
            throw new WebApplicationException(Status.NOT_ACCEPTABLE);
    }

    private void retrieveZIP(List<ArchiveInstanceLocator> refs,
            List<ArchiveInstanceLocator> instsfailed,
            List<ArchiveInstanceLocator> instscompleted, ZipOutput output) {
        for (ArchiveInstanceLocator ref : refs) {
            try {
                LocatorDatasetReader locatorDatasetReader;
                try {
                    locatorDatasetReader = new LocatorDatasetReader(ref, context, storescuService).read();
                } catch (IOException e) {
                    throw new WebApplicationException(e);
                }
                ArchiveInstanceLocator selectedLocator = locatorDatasetReader.getSelectedLocator();
                DatasetWithFMI datasetWithFMI = locatorDatasetReader.getDatasetWithFMI();

                String selectedTransferSyntaxUID = selectedLocator.tsuid;

                // prevent that (possibly) faulty JPEG-LS data leaves the system,
                // we only want to send it decompressed
                if (ref.getStorageSystem().getStorageSystemGroup().isPossiblyFaultyJPEGLS(datasetWithFMI)) {
                    selectedTransferSyntaxUID = UID.ExplicitVRLittleEndian;
                }

                output.addEntry(new DicomObjectOutput(datasetWithFMI.getDataset(), selectedLocator.tsuid, selectedTransferSyntaxUID, weightWatcher));
                instscompleted.add(ref);
            } catch (Exception e) {
                instsfailed.add(ref);
                LOG.error(
                        "Failed to add zip Entry for instance {} - Exception {}",
                        ref.iuid, e.getMessage());
            }
        }
    }

    private ArrayList<ArchiveInstanceLocator> extractExternalLocators(
            List<ArchiveInstanceLocator> refs) {
        ArrayList<ArchiveInstanceLocator> externalLocators = new ArrayList<ArchiveInstanceLocator>();
        for (Iterator<ArchiveInstanceLocator> iter = refs.iterator(); iter.hasNext();)
        {
            ArchiveInstanceLocator loc = iter.next();
            if (loc.getStorageSystem() == null) {
                externalLocators.add(loc);
                iter.remove();
            }
        }
        return externalLocators;
    }


    private Response retrievePixelData(final ArchiveInstanceLocator inst,
            final int... frames) {
        final String fileURI = inst.uri;
        final ArrayList<Integer> status =  new ArrayList<Integer>();
        final MultipartRelatedOutput output = new MultipartRelatedOutput();
        ArrayList<ArchiveInstanceLocator> locations = new ArrayList<ArchiveInstanceLocator>();
        locations.add(inst);
        ArrayList<ArchiveInstanceLocator> external = extractExternalLocators(locations);

        ArrayList<ArchiveInstanceLocator> failedToFetchForward = new ArrayList<ArchiveInstanceLocator>();
        if(!locations.isEmpty()) {
            status.add(addPixelDataTo(inst.getStorageSystem().getStorageSystemGroup(), fileURI, output, frames));
        }
        if(!external.isEmpty()) {
            FetchForwardCallBack fetchCallBack = new FetchForwardCallBack() {
                @Override
                public void onFetch(Collection<ArchiveInstanceLocator> instances,
                        BasicCStoreSCUResp resp) {
                    status.add(addPixelDataTo(inst.getStorageSystem().getStorageSystemGroup(), fileURI, output, frames));
                }
            };
            failedToFetchForward = fetchForwardService.fetchForward(aetitle, external, fetchCallBack, fetchCallBack);
        }

        if(!failedToFetchForward.isEmpty())
            throw new WebApplicationException(Status.NOT_FOUND);
        
        if (output.getParts().isEmpty())
            throw new WebApplicationException(Status.NOT_ACCEPTABLE);

        return Response.status( status.get(0).intValue()).entity(output).build();
    }

    private Response retrievePixelDataFromFile(StorageSystemGroup storageSystemGroup, String fileURI) {
        MultipartRelatedOutput output = new MultipartRelatedOutput();
        int status = addPixelDataTo(storageSystemGroup, fileURI, output, new int[]{});

        if (output.getParts().isEmpty())
            throw new WebApplicationException(Status.NOT_ACCEPTABLE);

        return Response.status(status).entity(output).build();
    }

    private Response retrieveBulkData(BulkData bulkData) {
        if (!acceptOctetStream)
            throw new WebApplicationException(Status.NOT_ACCEPTABLE);

        MultipartRelatedOutput output = new MultipartRelatedOutput();
        addPart(output, new BulkDataOutput(bulkData),
                MediaType.APPLICATION_OCTET_STREAM_TYPE, uriInfo
                        .getRequestUri().toString(), null);

        return Response.ok(output).build();
    }

    private Response retrieveMetadata(final List<ArchiveInstanceLocator> refs)
            throws DicomServiceException {
        StreamingOutput streamingOutput = null;
        final MultipartRelatedOutput multiPartOutput = new MultipartRelatedOutput();
        ResponseBuilder JSONResponseBuilder = null;
        if (refs.isEmpty())
            throw new WebApplicationException(Status.NOT_FOUND);

        if (!acceptDicomXML && !acceptDicomJSON && !acceptAll)
            throw new WebApplicationException(Status.NOT_ACCEPTABLE);

        if (acceptDicomJSON) {
            ArrayList<ArchiveInstanceLocator> external = extractExternalLocators(refs);
            // prefer local copies
            if (!external.isEmpty() && refs.isEmpty()) {
                ArrayList<ArchiveInstanceLocator> failedToFetchForward = new ArrayList<ArchiveInstanceLocator>();
                failedToFetchForward = fetchForwardService.fetchForward(aetitle, external, null, null);

                if (!failedToFetchForward.isEmpty()) {
                    for (Iterator<ArchiveInstanceLocator> iter = external.iterator(); iter.hasNext(); ) {
                        if (failedToFetchForward.contains(iter.next())) {
                            iter.remove();
                        }
                    }
                }
                refs.addAll(external);
            }
            streamingOutput = new DicomJSONOutput(aetitle, uriInfo, refs, context, storescuService);
        } else {
            ArrayList<ArchiveInstanceLocator> external = extractExternalLocators(refs);
            // prefer local copies
            if ((!refs.isEmpty() && !external.isEmpty()) 
                    || (!refs.isEmpty() && external.isEmpty())) {
                for (ArchiveInstanceLocator ref : refs)
                    addMetadataTo(ref, multiPartOutput);
            }
            else if (!external.isEmpty()) {
                FetchForwardCallBack fetchCallBack = new FetchForwardCallBack() {
                    @Override
                    public void onFetch(Collection<ArchiveInstanceLocator> instances,
                            BasicCStoreSCUResp resp) {
                        for (ArchiveInstanceLocator loc : instances)
                            addMetadataTo(loc, multiPartOutput);
                    }
                };
                
                fetchForwardService.fetchForward(aetitle, external, fetchCallBack, fetchCallBack);
            }
        }

        if (streamingOutput != null && acceptDicomJSON) {
            JSONResponseBuilder = Response.ok(streamingOutput);

            JSONResponseBuilder.header(CONTENT_TYPE,
                    MediaType.APPLICATION_JSON_TYPE);
            JSONResponseBuilder.header(CONTENT_ID,
                    ContentIDUtils.generateContentID());
            return JSONResponseBuilder.build();
        }
        else {
            return Response.ok(multiPartOutput).build();
        }

    }

    private boolean addDicomObjectTo(ArchiveInstanceLocator ref,
            MultipartRelatedOutput output) {
        LocatorDatasetReader locatorDatasetReader;
        try {
            locatorDatasetReader = new LocatorDatasetReader(ref, context, storescuService).read();
        } catch (IOException e) {
            throw new WebApplicationException(e);
        }
        ArchiveInstanceLocator selectedLocator = locatorDatasetReader.getSelectedLocator();
        DatasetWithFMI datasetWithFMI = locatorDatasetReader.getDatasetWithFMI();

        String selectedTransferSyntaxUID = selectDicomTransferSyntaxes(ref, datasetWithFMI);
        if (selectedTransferSyntaxUID == null) {
            return false;
        }
        addPart(output,
                new DicomObjectOutput(datasetWithFMI.getDataset(), selectedLocator.tsuid, selectedTransferSyntaxUID, weightWatcher),
                MediaType.valueOf("application/dicom;transfer-syntax=" + selectedTransferSyntaxUID),
                null, ref.iuid);
        return true;
    }

    private int addPixelDataTo(StorageSystemGroup storageSystemGroup, String fileURI, MultipartRelatedOutput output, int... frameList) {
        try {
            LOG.info("Add Pixel Data [file={}]",fileURI);

            DatasetWithFMI datasetWithFMI;
            String transferSyntaxUID;
            try (DicomInputStream din = new DicomInputStream(new File(new URI(fileURI)))) {
                din.setIncludeBulkData(IncludeBulkData.URI);
                datasetWithFMI = din.readDatasetWithFMI();
                transferSyntaxUID = din.getTransferSyntax();
            }

            // note: un-coerced SOPInstanceUID! (could be QCed, or whatever)
            String uncoercedIuid = datasetWithFMI.getDataset().getString(Tag.SOPInstanceUID);

            MediaType mediaType = selectBulkdataMediaTypeForTransferSyntax(storageSystemGroup, datasetWithFMI, transferSyntaxUID);
            if (mediaType == null) {
                LOG.info(
                        "{}: Failed to retrieve Pixel Data of Instance[uid={}]: Requested Transfer Syntax not supported",
                        method, uncoercedIuid);
                return STATUS_NOT_ACCEPTABLE;
            }

            if (isMultiframeMediaType(mediaType) && frameList.length > 0) {
                LOG.info(
                        "{}: Failed to retrieve Frame Pixel Data of Instance[uid={}]: Not supported for Content-Type={}",
                        new Object[]{method, uncoercedIuid, mediaType});
                return STATUS_NOT_ACCEPTABLE;
            }

            Object pixeldata = datasetWithFMI.getDataset().getValue(Tag.PixelData);
            if (pixeldata == null) {
                LOG.info(
                        "{}: Failed to retrieve Pixel Data of Instance[uid={}]: Not an image",
                        method, uncoercedIuid);
                return STATUS_NOT_ACCEPTABLE;
            }

            int frames = datasetWithFMI.getDataset().getInt(Tag.NumberOfFrames, 1);
            int[] adjustedFrameList = adjustFrameList(uncoercedIuid, frameList, frames);

            String bulkDataURI = toBulkDataURI(fileURI);
            if (pixeldata instanceof Fragments) {
                Fragments bulkData = (Fragments) pixeldata;
                if (mediaType == MediaType.APPLICATION_OCTET_STREAM_TYPE) {
                    addDecompressedPixelDataTo(datasetWithFMI.getDataset(), transferSyntaxUID, adjustedFrameList, output, bulkDataURI, uncoercedIuid);
                } else {
                    addCompressedPixelDataTo(bulkData, frames,
                            adjustedFrameList, output, mediaType, bulkDataURI,
                            uncoercedIuid);
                }
            } else {
                BulkData bulkData = (BulkData) pixeldata;
                addUncompressedPixelDataTo(bulkData, datasetWithFMI.getDataset(), adjustedFrameList,
                        output, bulkDataURI, uncoercedIuid);
            }
            return adjustedFrameList.length < frameList.length ? STATUS_PARTIAL_CONTENT
                    : STATUS_OK;
        } catch (FileNotFoundException e) {
            throw new WebApplicationException(Status.NOT_FOUND);
        } catch (IOException e) {
            throw new WebApplicationException(e);
        } catch (URISyntaxException e) {
            throw new WebApplicationException(e);
        }
    }

    private int[] adjustFrameList(String iuid, int[] frameList, int frames) {
        int n = 0;
        for (int i = 0; i < frameList.length; i++) {
            if (frameList[i] <= frames)
                swap(frameList, n++, i);
        }
        if (n == frameList.length)
            return frameList;

        int[] skipped = new int[frameList.length - n];
        System.arraycopy(frameList, n, skipped, 0, skipped.length);
        LOG.info(
                "{}, Failed to retrieve Frames {} of Pixel Data of Instance[uid={}]: NumberOfFrames={}",
                new Object[] { method, Arrays.toString(skipped), iuid, frames });
        if (n == 0)
            throw new WebApplicationException(Status.NOT_FOUND);

        return Arrays.copyOf(frameList, n);
    }

    private static void swap(int[] a, int i, int j) {
        if (i != j) {
            int tmp = a[i];
            a[i] = a[j];
            a[j] = tmp;
        }
    }

    private void addDecompressedPixelDataTo(Attributes dataset, String tsuid,
            int[] frameList, MultipartRelatedOutput output, String bulkDataURI,
            String iuid) {
        if (frameList.length == 0) {
            addPart(output, new DecompressedPixelDataOutput(dataset, tsuid, -1, weightWatcher),
                    MediaType.APPLICATION_OCTET_STREAM_TYPE, bulkDataURI, iuid);
        } else
            for (int frame : frameList) {
                addPart(output, new DecompressedPixelDataOutput(dataset, tsuid, frame - 1, weightWatcher),
                        MediaType.APPLICATION_OCTET_STREAM_TYPE, bulkDataURI + "/frames/" + frame, iuid);
            }
    }

    private void addPart(MultipartRelatedOutput output, Object entity,
            MediaType mediaType, String contentLocation, String iuid) {
        OutputPart part = output.addPart(entity, mediaType);
        MultivaluedMap<String, Object> headerParams = part.getHeaders();
        headerParams.add(CONTENT_TYPE, mediaType);
        headerParams.add(CONTENT_ID, ContentIDUtils.generateContentID());
        if (contentLocation != null)
            headerParams.add(CONTENT_LOCATION, contentLocation);
    }

    private void addCompressedPixelDataTo(Fragments fragments, int frames,
            int[] adjustedFrameList, MultipartRelatedOutput output,
            MediaType mediaType, String bulkDataURI, String iuid) {
        if (frames == 1 || isMultiframeMediaType(mediaType)) {
            addPart(output, new CompressedPixelDataOutput(fragments),
                    mediaType, bulkDataURI, iuid);
        } else if (adjustedFrameList.length == 0) {
            for (int frame = 1; frame <= frames; frame++) {
                addPart(output,
                        new BulkDataOutput((BulkData) fragments.get(frame)),
                        mediaType, bulkDataURI + "/frames/" + frame, iuid);
            }
        } else {
            for (int frame : adjustedFrameList) {
                addPart(output,
                        new BulkDataOutput((BulkData) fragments.get(frame)),
                        mediaType, bulkDataURI + "/frames/" + frame, iuid);
            }
        }
    }

    private void addUncompressedPixelDataTo(BulkData bulkData, Attributes ds,
            int[] adjustedFrameList, MultipartRelatedOutput output,
            String bulkDataURI, String iuid) {
        if (adjustedFrameList.length == 0) {
            addPart(output, new BulkDataOutput(bulkData),
                    MediaType.APPLICATION_OCTET_STREAM_TYPE, bulkDataURI, iuid);
        } else {
            int rows = ds.getInt(Tag.Rows, 0);
            int cols = ds.getInt(Tag.Columns, 0);
            int samples = ds.getInt(Tag.SamplesPerPixel, 0);
            int bitsAllocated = ds.getInt(Tag.BitsAllocated, 8);
            int frameLength = rows * cols * samples * (bitsAllocated >>> 3);
            for (int frame : adjustedFrameList) {
                addPart(output,
                        new BulkDataOutput(new BulkData(bulkData
                                .uriWithoutQuery(), bulkData.offset()
                                + (frame - 1) * frameLength, frameLength, ds
                                .bigEndian())),
                        MediaType.APPLICATION_OCTET_STREAM_TYPE, bulkDataURI
                                + "/frames/" + frame, iuid);
            }
        }
    }

    private void addMetadataTo(ArchiveInstanceLocator ref,
            MultipartRelatedOutput output) {
        Attributes attrs = (Attributes) ref.getObject();
        addPart(output, new DicomXMLOutput(ref, toBulkDataURI(ref.uri), attrs,
                context, storescuService), MediaTypes.APPLICATION_DICOM_XML_TYPE, null, ref.iuid);
    }

    private boolean isMultiframeMediaType(MediaType mediaType) {
        return mediaType.getType().equalsIgnoreCase("video")
                || mediaType.getSubtype().equalsIgnoreCase("dicom+jpeg-jpx");
    }

    private Attributes getFileAttributes(ArchiveInstanceLocator ref) {
        DicomInputStream dis = null;
        try {
            dis = new DicomInputStream(storescuService.getFile(ref).toFile());
            dis.setIncludeBulkData(IncludeBulkData.URI);
            Attributes dataset = dis.readDataset(-1, -1);
            return dataset;
        } catch (IOException e) {
            LOG.error(
                    "Unable to read file, Exception {}, using the blob for coercion - (Incomplete Coercion)",
                    e);
            return (Attributes) ref.getObject();
        } finally {
            SafeClose.close(dis);
        }
    }
}
