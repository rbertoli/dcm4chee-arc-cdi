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

package org.dcm4chee.archive.store;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import javax.persistence.EntityManager;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.archive.conf.StoreAction;
import org.dcm4chee.archive.entity.Instance;
import org.dcm4chee.archive.entity.Patient;
import org.dcm4chee.archive.entity.Series;
import org.dcm4chee.archive.entity.Study;
import org.dcm4chee.storage.StorageContext;

/**
 * The Store Service stores instances to the Archive. This involves writing files to a Storage System and updating the
 * database.
 *
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public interface StoreService {

    int DATA_SET_NOT_PARSEABLE = 0xC900;

    StoreSession createStoreSession(StoreService storeService)
            throws DicomServiceException;

    StoreContext createStoreContext(StoreSession session);

    void init(StoreSession session) throws DicomServiceException;

    void writeSpoolFile(StoreContext session, Attributes fmi, Attributes attrs)
            throws DicomServiceException;

    void writeSpoolFile(StoreContext context, Attributes fmi, InputStream data)
            throws DicomServiceException;

    void onClose(StoreSession session);

    void spool(StoreContext context) throws DicomServiceException;

    void store(StoreContext context) throws DicomServiceException;

    Path spool(StoreSession session, InputStream in, String suffix)
            throws IOException;

    void coerceAttributes(StoreContext context) throws DicomServiceException;

    StorageContext processFile(StoreContext context) throws DicomServiceException;

    void updateDB(StoreContext context) throws DicomServiceException;

    Instance findOrCreateInstance(EntityManager em, StoreContext context)
            throws DicomServiceException;

    Series findOrCreateSeries(EntityManager em, StoreContext context)
            throws DicomServiceException;

    Study findOrCreateStudy(EntityManager em, StoreContext context)
            throws DicomServiceException;

    Patient findOrCreatePatient(EntityManager em, StoreContext context)
            throws DicomServiceException;

    StoreAction instanceExists(EntityManager em, StoreContext context,
            Instance instance) throws DicomServiceException;

    void cleanup(StoreContext context);

    void fireStoreEvent(StoreContext context);

    StorageContext storeMetaData(StoreContext context) throws DicomServiceException;

    void beginProcessFile(StoreContext context);

    void beginStoreMetadata(StoreContext context);

    Instance adjustForNoneIOCM(Instance instanceToStore, StoreContext context);
}
