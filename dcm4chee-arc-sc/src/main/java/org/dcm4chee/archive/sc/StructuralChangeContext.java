//
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

package org.dcm4chee.archive.sc;

import java.util.Set;

/**
 * Describes a structural change performed on an existing study done on PATIENT/STUDY/SERIES/INSTANCE level.
 * 
 * A structural change has associated structural change types which are assumed to form an ordered hierarchy.
 * 
 * @author Alexander Hoermandinger <alexander.hoermandinger@agfa.com>
 *
 */
public interface StructuralChangeContext {

    /**
     * @return Returns the complete structural change type hierarchy associated with the change
     */
    Enum<?>[] getChangeTypeHierarchy();
    
    /**
     * @param changeType Returns <code>true</code> if the given type is part of the associated change type hierarchy,
     * returns <code>false</code> otherwise
     * @return
     */
    boolean hasChangeType(Enum<?> changeType);

    /**
     * @param changeType
     * @return  Returns the sub-hierarchy starting from the given change type, returns <code>null</code> if the given
     * change type is not contained in the hierarchy
     */
    Enum<?>[] getSubChangeTypeHierarchy(Enum<?> changeType);
    
    /**
     * @param changeTypeClass
     * @return Returns the value for a given change type class contained in the change type hierarchy. 
     * If the change type is not contained in the hierarchy then <code>null</code> is returned.
     */
    <T extends Enum<?>> T getChangeTypeValue(Class<T> changeTypeClass);
  
    long getTimestamp();

    Set<String> getAffectedStudyUIDs();
    
    Set<String> getAffectedSeriesUIDs();
    
    Set<InstanceIdentifier> getAffectedInstances();
    
    Set<InstanceIdentifier> getSourceInstances();
    
    Set<InstanceIdentifier> getTargetInstances();
    
    interface InstanceIdentifier {
        
        String getStudyInstanceUID();
        
        String getSeriesInstanceUID();
        
        String getSopInstanceUID();
        
    }
}
