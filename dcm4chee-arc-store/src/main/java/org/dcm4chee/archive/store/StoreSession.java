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

import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4chee.archive.conf.ArchiveAEExtension;
import org.dcm4chee.archive.conf.StoreParam;
import org.dcm4chee.archive.dto.Participant;
import org.dcm4chee.storage.conf.StorageSystem;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.TimeZone;

/**
 * Aggregates a number of useful properties related to the ongoing store,
 * transaction.  There is one StoreSession instance per Association.
 *
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public interface StoreSession {

    Participant getSource();

    void setSource(Participant source);

    StoreService getStoreService();

    String getRemoteAET();

    void setRemoteAET(String remoteAET);

    String getLocalAET();

    StorageSystem getStorageSystem();

    void setStorageSystem(StorageSystem storageSystem);

    void setSpoolStorageSystem(StorageSystem spoolStorageSystem);

    StorageSystem getSpoolStorageSystem();

    StorageSystem getMetaDataStorageSystem();

    void setMetaDataStorageSystem(StorageSystem storageSystem);

    Path getSpoolDirectory();

    void setSpoolDirectory(Path spoolDirectory);

    ArchiveAEExtension getArchiveAEExtension();

    void setArchiveAEExtension(ArchiveAEExtension arcAE);

    MessageDigest getMessageDigest();

    void setMessageDigest(MessageDigest messageDigest);

    StoreParam getStoreParam();

    void setStoreParam(StoreParam storeParam);

    Object getProperty(String key);

    Object removeProperty(String key);

    void setProperty(String key, Object value);

    Device getDevice();

    Device getSourceDevice();

    void setRemoteApplicationEntity(ApplicationEntity ae);

    String getSourceDeviceName();

    TimeZone getSourceDeviceTimeZone();

    List<String> getStoredFiles();

    void addStoredFile(String storedFile);
}
