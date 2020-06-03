/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.carbon.apimgt.rest.api.endpoint.registry.impl;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.jaxrs.ext.MessageContext;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.apimgt.api.endpoint.registry.api.DefinitionValidationException;
import org.wso2.carbon.apimgt.api.endpoint.registry.model.EndpointRegistryEntryFilterParams;
import org.wso2.carbon.apimgt.api.endpoint.registry.api.EndpointRegistry;
import org.wso2.carbon.apimgt.impl.endpoint.registry.impl.EndpointRegistryImpl;
import org.wso2.carbon.apimgt.api.endpoint.registry.model.EndpointRegistryEntry;
import org.wso2.carbon.apimgt.api.endpoint.registry.api.EndpointRegistryException;
import org.wso2.carbon.apimgt.api.endpoint.registry.model.EndpointRegistryInfo;
import org.wso2.carbon.apimgt.api.endpoint.registry.api.EndpointRegistryResourceAlreadyExistsException;
import org.wso2.carbon.apimgt.impl.endpoint.registry.util.EndpointRegistryUtil;
import org.wso2.carbon.apimgt.impl.importexport.utils.CommonUtil;
import org.wso2.carbon.apimgt.rest.api.endpoint.registry.RegistriesApi;
import org.wso2.carbon.apimgt.rest.api.endpoint.registry.RegistriesApiService;

import org.apache.cxf.jaxrs.ext.multipart.Attachment;

import org.wso2.carbon.apimgt.rest.api.endpoint.registry.dto.RegistryArrayDTO;
import org.wso2.carbon.apimgt.rest.api.endpoint.registry.dto.RegistryDTO;
import org.wso2.carbon.apimgt.rest.api.endpoint.registry.dto.RegistryEntryArrayDTO;
import org.wso2.carbon.apimgt.rest.api.endpoint.registry.dto.RegistryEntryDTO;
import org.wso2.carbon.apimgt.rest.api.endpoint.registry.util.EndpointRegistryMappingUtils;
import org.wso2.carbon.apimgt.rest.api.util.utils.RestApiUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@RequestScoped
public class RegistriesApiServiceImpl implements RegistriesApiService {

    private static final Log log = LogFactory.getLog(RegistriesApiServiceImpl.class);
    private static final Log audit = CarbonConstants.AUDIT_LOG;

    @Override
    public Response getAllEntriesInRegistry(String registryId, String version,
                                            RegistriesApi.ServiceTypeEnum serviceType,
                                            RegistriesApi.DefinitionTypeEnum definitionType,
                                            String name, String displayName,
                                            RegistriesApi.ServiceCategoryEnum serviceCategory,
                                            RegistriesApi.SortEntryByEnum sortEntryBy,
                                            RegistriesApi.SortEntryOrderEnum sortEntryOrder,
                                            Integer limit, Integer offset, MessageContext messageContext) {
        String tenantDomain = RestApiUtil.getLoggedInUserTenantDomain();
        String user = RestApiUtil.getLoggedInUsername();
        RegistryEntryArrayDTO registryEntryArray = new RegistryEntryArrayDTO();
        EndpointRegistry registryProvider = new EndpointRegistryImpl(user);
        try {
            getEndpointRegistry(registryId, tenantDomain, registryProvider);

            EndpointRegistryEntryFilterParams filterParams = EndpointRegistryMappingUtils
                    .getRegistryEntryFilterParams(
                    name, displayName, version, serviceType, serviceCategory, definitionType,
                            sortEntryBy, sortEntryOrder, limit, offset);
            List<EndpointRegistryEntry> endpointRegistryEntryList =
                    registryProvider.getEndpointRegistryEntries(filterParams, registryId);
            for (EndpointRegistryEntry endpointRegistryEntry : endpointRegistryEntryList) {
                registryEntryArray.add(EndpointRegistryMappingUtils.fromRegistryEntryToDTO(endpointRegistryEntry));
            }
            return Response.ok().entity(registryEntryArray).build();
        } catch (EndpointRegistryException e) {
            RestApiUtil.handleInternalServerError("Error while retrieving entries of endpoint registry " +
                    "given by id: " + registryId, e, log);
        }
        return null;
    }

    @Override
    public Response getRegistryByUUID(String registryId, MessageContext messageContext) {
        String tenantDomain = RestApiUtil.getLoggedInUserTenantDomain();
        String user = RestApiUtil.getLoggedInUsername();
        EndpointRegistry registryProvider = new EndpointRegistryImpl(user);
        try {
            EndpointRegistryInfo endpointRegistryInfo = getEndpointRegistry(registryId, tenantDomain, registryProvider);
            if (endpointRegistryInfo != null) {
                RegistryDTO registryDTO = EndpointRegistryMappingUtils.fromEndpointRegistryToDTO(endpointRegistryInfo);
                return Response.ok().entity(registryDTO).build();
            }
        } catch (EndpointRegistryException e) {
            RestApiUtil.handleInternalServerError("Error while retrieving details of endpoint registry by id: "
                    + registryId, e, log);
        }
        return null;
    }

    @Override
    public Response getRegistries(MessageContext messageContext) {
        String tenantDomain = RestApiUtil.getLoggedInUserTenantDomain();
        String user = RestApiUtil.getLoggedInUsername();
        EndpointRegistry registryProvider = new EndpointRegistryImpl(user);
        RegistryArrayDTO registryDTOList = new RegistryArrayDTO();

        try {
            List<EndpointRegistryInfo> endpointRegistryInfoList =
                    registryProvider.getEndpointRegistries(tenantDomain);
            for (EndpointRegistryInfo endpointRegistryInfo : endpointRegistryInfoList) {
                registryDTOList.add(EndpointRegistryMappingUtils.fromEndpointRegistryToDTO(endpointRegistryInfo));
            }
            return Response.ok().entity(registryDTOList).build();
        } catch (EndpointRegistryException e) {
            RestApiUtil.handleInternalServerError("Error while retrieving details of endpoint registries", e, log);
        }
        return null;
    }

    @Override
    public Response addRegistry(RegistryDTO body, MessageContext messageContext) {
        String tenantDomain = RestApiUtil.getLoggedInUserTenantDomain();
        String user = RestApiUtil.getLoggedInUsername();
        EndpointRegistryInfo registry = EndpointRegistryMappingUtils.fromDTOtoEndpointRegistry(body, user);
        try {
            EndpointRegistry registryProvider = new EndpointRegistryImpl(user);
            String registryId = registryProvider.addEndpointRegistry(registry);
            EndpointRegistryInfo createdRegistry = registryProvider.getEndpointRegistryByUUID(registryId, tenantDomain);

            audit.info("Successfully created endpoint registry " + createdRegistry.getName() + " with id :"
                    + createdRegistry.getUuid() + " by :" + user);
            return Response.ok()
                    .entity(EndpointRegistryMappingUtils.fromEndpointRegistryToDTO(createdRegistry))
                    .build();
        } catch (EndpointRegistryResourceAlreadyExistsException e) {
            RestApiUtil.handleResourceAlreadyExistsError(e.getMessage(), e, log);
        } catch (EndpointRegistryException e) {
            RestApiUtil.handleInternalServerError("Error while adding new endpoint registry: "
                    + registry.getName(), e, log);
        }
        return null;
    }

    @Override
    public Response createRegistryEntry(String registryId, RegistryEntryDTO registryEntry, InputStream
            definitionFileInputStream, Attachment definitionFileDetail, MessageContext messageContext) {
        String tenantDomain = RestApiUtil.getLoggedInUserTenantDomain();
        String user = RestApiUtil.getLoggedInUsername();
        EndpointRegistry registryProvider = new EndpointRegistryImpl(user);
        try {
            EndpointRegistryInfo endpointRegistry = getEndpointRegistry(registryId, tenantDomain, registryProvider);
            InputStream definitionFile = validateAndRetrieveDefinition(definitionFileInputStream, registryEntry);
            EndpointRegistryEntry entryToAdd = EndpointRegistryMappingUtils.fromDTOToRegistryEntry(registryEntry,
                    null, definitionFile, endpointRegistry.getRegistryId());
            String entryId = registryProvider.addEndpointRegistryEntry(entryToAdd);
            EndpointRegistryEntry createdEntry = registryProvider.getEndpointRegistryEntryByUUID(registryId, entryId);
            audit.info("Successfully created endpoint registry entry with id :" + createdEntry.getEntryId() +
                    " in :" + registryId + " by:" + user);
            return Response.ok().entity(EndpointRegistryMappingUtils.fromRegistryEntryToDTO(createdEntry)).build();
        } catch (EndpointRegistryResourceAlreadyExistsException e) {
            RestApiUtil.handleResourceAlreadyExistsError("Endpoint Registry Entry with name '"
                    + registryEntry.getEntryName() + "' already exists", e, log);
        } catch (DefinitionValidationException e) {
            RestApiUtil.handleBadRequest("Error while validating the endpoint definition of " +
                    "the new registry entry with registry id: " + registryId, e, log);
        } catch (EndpointRegistryException e) {
            RestApiUtil.handleInternalServerError("Error while adding new entry for the endpoint registry " +
                    "with id: " + registryId, e, log);
        }
        return null;
    }

    @Override
    public Response updateRegistry(RegistryDTO body, String registryId, MessageContext messageContext) {

        String tenantDomain = RestApiUtil.getLoggedInUserTenantDomain();
        String user = RestApiUtil.getLoggedInUsername();
        EndpointRegistry registryProvider = new EndpointRegistryImpl(user);
        EndpointRegistryInfo registryToUpdate = EndpointRegistryMappingUtils.fromDTOtoEndpointRegistry(body, user);
        try {
            EndpointRegistryInfo endpointRegistry = getEndpointRegistry(registryId, tenantDomain, registryProvider);
            registryProvider.updateEndpointRegistry(registryId, endpointRegistry.getDisplayName(),
                    endpointRegistry.getType(), registryToUpdate);
            EndpointRegistryInfo updatedEndpointRegistry
                    = registryProvider.getEndpointRegistryByUUID(registryId, tenantDomain);
            audit.info("Successfully updated endpoint registry of id :" + updatedEndpointRegistry.getUuid()
                    + " by :" + user);
            return Response.ok()
                    .entity(EndpointRegistryMappingUtils.fromEndpointRegistryToDTO(updatedEndpointRegistry)).build();
        } catch (EndpointRegistryResourceAlreadyExistsException e) {
            RestApiUtil.handleResourceAlreadyExistsError(e.getMessage(), e, log);
        } catch (EndpointRegistryException e) {
            RestApiUtil.handleInternalServerError("Error while updating the endpoint registry " +
                    "with id: " + registryId, e, log);
        }
        return null;
    }

    @Override
    public Response deleteRegistry(String registryId, MessageContext messageContext) {
        String tenantDomain = RestApiUtil.getLoggedInUserTenantDomain();
        String user = RestApiUtil.getLoggedInUsername();
        EndpointRegistry registryProvider = new EndpointRegistryImpl(user);
        try {
            getEndpointRegistry(registryId, tenantDomain, registryProvider);
            registryProvider.deleteEndpointRegistry(registryId);
            audit.info("Successfully deleted endpoint registry of id :" + registryId + " by :" + user);
            return Response.ok().build();
        } catch (EndpointRegistryException e) {
            RestApiUtil.handleInternalServerError("Error while deleting the endpoint registry " +
                    "with id: " + registryId, e, log);
        }
        return null;
    }

    @Override
    public Response getRegistryEntryByUuid(String registryId, String entryId, MessageContext messageContext) {
        String tenantDomain = RestApiUtil.getLoggedInUserTenantDomain();
        String user = RestApiUtil.getLoggedInUsername();
        EndpointRegistry registryProvider = new EndpointRegistryImpl(user);
        try {
            getEndpointRegistry(registryId, tenantDomain, registryProvider);
            EndpointRegistryEntry endpointRegistryEntry =
                    getEndpointRegistryEntry(registryId, entryId, registryProvider);
            if (endpointRegistryEntry != null) {
                return Response.ok().entity(EndpointRegistryMappingUtils.fromRegistryEntryToDTO(endpointRegistryEntry))
                        .build();
            }
        } catch (EndpointRegistryException e) {
            RestApiUtil.handleInternalServerError("Error while fetching endpoint registry entry: "
                    + entryId, e, log);
        }
        return null;
    }

    @Override
    public Response updateRegistryEntry(String registryId, String entryId, RegistryEntryDTO registryEntry, InputStream
            definitionFileInputStream, Attachment definitionFileDetail, MessageContext messageContext) {
        String tenantDomain = RestApiUtil.getLoggedInUserTenantDomain();
        String user = RestApiUtil.getLoggedInUsername();
        EndpointRegistry registryProvider = new EndpointRegistryImpl(user);

        try {
            EndpointRegistryInfo endpointRegistry = getEndpointRegistry(registryId, tenantDomain, registryProvider);
            EndpointRegistryEntry endpointRegistryEntry =
                    getEndpointRegistryEntry(registryId, entryId, registryProvider);
            InputStream definitionFile = validateAndRetrieveDefinition(definitionFileInputStream, registryEntry);
            EndpointRegistryEntry entryToUpdate = EndpointRegistryMappingUtils.fromDTOToRegistryEntry(registryEntry,
                    entryId, definitionFile, endpointRegistry.getRegistryId());

            registryProvider.updateEndpointRegistryEntry(endpointRegistryEntry.getDisplayName(), entryToUpdate);

            EndpointRegistryEntry updatedEntry = registryProvider.getEndpointRegistryEntryByUUID(registryId, entryId);
            audit.info("Successfully updated endpoint registry entry with id :" + entryId +
                    " in :" + registryId + " by:" + user);
            return Response.ok().entity(EndpointRegistryMappingUtils.fromRegistryEntryToDTO(updatedEntry)).build();
        } catch (EndpointRegistryResourceAlreadyExistsException e) {
            RestApiUtil.handleResourceAlreadyExistsError(e.getMessage(), e, log);
        } catch (DefinitionValidationException e) {
            RestApiUtil.handleBadRequest("Error while validating the endpoint definition of " +
                    "the registry entry with id: " + entryId, e, log);
        } catch (EndpointRegistryException e) {
            RestApiUtil.handleInternalServerError("Error while updating the endpoint registry entry " +
                    "with id: " + entryId, e, log);
        }
        return null;
    }

    @Override
    public Response deleteRegistryEntry(String registryId, String entryId, MessageContext messageContext) {
        String tenantDomain = RestApiUtil.getLoggedInUserTenantDomain();
        String user = RestApiUtil.getLoggedInUsername();
        EndpointRegistry registryProvider = new EndpointRegistryImpl(user);
        try {
            getEndpointRegistry(registryId, tenantDomain, registryProvider);
            getEndpointRegistryEntry(registryId, entryId, registryProvider);

            registryProvider.deleteEndpointRegistryEntry(entryId);
            audit.info("Successfully deleted endpoint registry entry with id :" + entryId +
                    " in :" + registryId + " by:" + user);
            return Response.ok().build();
        } catch (EndpointRegistryException e) {
            RestApiUtil.handleInternalServerError("Error while deleting the endpoint registry entry " +
                    "with id: " + registryId, e, log);
        }
        return null;
    }

    @Override
    public Response createNewEntryVersion(String registryId, String entryId, String version,
                                          MessageContext messageContext) throws EndpointRegistryException {
        String tenantDomain = RestApiUtil.getLoggedInUserTenantDomain();
        String user = RestApiUtil.getLoggedInUsername();
        EndpointRegistry registryProvider = new EndpointRegistryImpl(user);
        try {
            EndpointRegistryInfo endpointRegistry =
                    registryProvider.getEndpointRegistryByUUID(registryId, tenantDomain);
            if (endpointRegistry == null) {
                RestApiUtil.handleResourceNotFoundError("Endpoint registry with the id: " + registryId +
                        " is not found", log);
            }
            EndpointRegistryEntry endpointRegistryEntry =
                    registryProvider.getEndpointRegistryEntryByUUID(registryId, entryId);
            if (endpointRegistryEntry == null) {
                RestApiUtil.handleResourceNotFoundError("Endpoint registry entry with the id: " + entryId +
                        " is not found", log);
            }
            endpointRegistryEntry.setVersion(version);
            String newEntryID =
                    registryProvider.createNewEntryVersion(entryId, endpointRegistryEntry);
            audit.info("Successfully created new version: '" + version + "' of endpoint registry entry with id :"
                    + entryId + " in :" + registryId + " by:" + user);
            EndpointRegistryEntry endpointRegistryEntryNewVersion
                    = registryProvider.getEndpointRegistryEntryByUUID(registryId, newEntryID);
            if (endpointRegistryEntryNewVersion == null) {
                RestApiUtil.handleResourceNotFoundError("Endpoint registry entry with the id: " + entryId +
                        " is not found", log);
            }
            audit.info("Successfully created the new version '" + version + "' endpoint registry entry " +
                    "with id :" + entryId + " in :" + registryId + " by:" + user);
            return Response.ok()
                    .entity(EndpointRegistryMappingUtils.fromRegistryEntryToDTO(endpointRegistryEntryNewVersion))
                    .build();
        } catch (EndpointRegistryResourceAlreadyExistsException e) {
            RestApiUtil.handleResourceAlreadyExistsError("Endpoint Registry Entry with version '"
                    + version + "' already exists for the entry with id: " + entryId, e, log);
        } catch (EndpointRegistryException e) {
            RestApiUtil.handleInternalServerError("Error while creating the new version of the " +
                    "endpoint registry entry with id: " + registryId, e, log);
        }
        return null;
    }

    @Override
    public Response getEndpointDefinition(String registryId, String entryId, MessageContext messageContext) {
        String tenantDomain = RestApiUtil.getLoggedInUserTenantDomain();
        String user = RestApiUtil.getLoggedInUsername();
        String contentType = StringUtils.EMPTY;
        EndpointRegistry registryProvider = new EndpointRegistryImpl(user);
        try {
            getEndpointRegistry(registryId, tenantDomain, registryProvider);
            EndpointRegistryEntry registryEntry = getEndpointRegistryEntry(registryId, entryId, registryProvider);

            String type = registryEntry.getDefinitionType();
            if (RegistryEntryDTO.DefinitionTypeEnum.OAS.equals(RegistryEntryDTO.DefinitionTypeEnum.fromValue(type))
                    || RegistryEntryDTO.DefinitionTypeEnum.GQL_SDL.equals(RegistryEntryDTO.DefinitionTypeEnum
                    .fromValue(type))) {
                contentType = MediaType.APPLICATION_JSON;
            } else if (RegistryEntryDTO.DefinitionTypeEnum.WSDL1.equals(RegistryEntryDTO.DefinitionTypeEnum
                    .fromValue(type)) || RegistryEntryDTO.DefinitionTypeEnum.WSDL2.equals(RegistryEntryDTO
                    .DefinitionTypeEnum.fromValue(type))) {
                contentType = MediaType.TEXT_XML;
            }
            InputStream endpointDefinition = registryEntry.getEndpointDefinition();
            if (endpointDefinition == null) {
                RestApiUtil.handleResourceNotFoundError("Endpoint definition not found for entry with ID: "
                        + entryId, log);
            } else {
                return Response.ok(endpointDefinition).type(contentType).build();
            }
        } catch (EndpointRegistryException e) {
            RestApiUtil.handleInternalServerError("Error while retrieving the endpoint definition of registry " +
                    "entry with id: " + entryId, e, log);
        }
        return null;
    }

    private byte[] getDefinitionFromInput(InputStream definitionFileInputStream) throws IOException {

        ByteArrayOutputStream definitionFileOutputByteStream = new ByteArrayOutputStream();
        IOUtils.copy(definitionFileInputStream, definitionFileOutputByteStream);
        return definitionFileOutputByteStream.toByteArray();
    }

    private byte[] transformDefinitionContent(byte[] definitionFileByteArray,
                                              RegistryEntryDTO.DefinitionTypeEnum type) throws IOException {

        if (RegistryEntryDTO.DefinitionTypeEnum.OAS.equals(type)) {
            String oasContent = new String(definitionFileByteArray);
            if (!oasContent.trim().startsWith("{")) {
                String jsonContent = CommonUtil.yamlToJson(oasContent);
                return jsonContent.getBytes(StandardCharsets.UTF_8);
            }
        }
        return definitionFileByteArray;
    }

    private EndpointRegistryInfo getEndpointRegistry(String registryId, String tenantDomain,
                                                     EndpointRegistry registryProvider)
            throws EndpointRegistryException {

        EndpointRegistryInfo endpointRegistry = registryProvider.getEndpointRegistryByUUID(registryId, tenantDomain);
        if (endpointRegistry == null) {
            RestApiUtil.handleResourceNotFoundError("Endpoint registry with the id: " + registryId +
                    " is not found", log);
        }
        return endpointRegistry;
    }

    private EndpointRegistryEntry getEndpointRegistryEntry(String registryId, String entryId,
                                                           EndpointRegistry registryProvider)
            throws EndpointRegistryException {

        EndpointRegistryEntry registryEntry = registryProvider.getEndpointRegistryEntryByUUID(registryId, entryId);
        if (registryEntry == null) {
            RestApiUtil.handleResourceNotFoundError("Endpoint registry entry with the id: " + entryId +
                    " is not found", log);
        }
        return registryEntry;
    }

    private InputStream validateAndRetrieveDefinition(InputStream definitionFileInputStream,
                                                      RegistryEntryDTO registryEntry)
            throws DefinitionValidationException {

        InputStream definitionFile = null;

        if ((definitionFileInputStream != null || registryEntry.getDefinitionUrl() != null) &&
                registryEntry.getDefinitionType() == null) {
            RestApiUtil.handleBadRequest("Missing definitionType parameter", log);
        }

        try {
            if (definitionFileInputStream != null) {
                // Retrieve definition from the file
                byte[] definitionFileByteArray = getDefinitionFromInput(definitionFileInputStream);

                if (EndpointRegistryUtil.isValidDefinition(definitionFileByteArray,
                        registryEntry.getDefinitionType().toString())) {
                    definitionFileByteArray = transformDefinitionContent(definitionFileByteArray,
                            registryEntry.getDefinitionType());
                    definitionFile = new ByteArrayInputStream(definitionFileByteArray);
                } else {
                    throw new DefinitionValidationException("Endpoint definition content of entry with name: "
                            + registryEntry.getEntryName() + " is invalid");
                }
            } else if (registryEntry.getDefinitionUrl() != null) {
                // Retrieve the endpoint definition from URL
                URL definitionURL = new URL(registryEntry.getDefinitionUrl());

                if (!EndpointRegistryUtil.isValidDefinition(definitionURL,
                        registryEntry.getDefinitionType().toString())) {
                    throw new DefinitionValidationException("Endpoint definition content of entry with name: "
                            + registryEntry.getEntryName() + " is invalid");
                }
            }
        } catch (IOException e) {
            throw new DefinitionValidationException("Error while reading content in the endpoint definition of " +
                    "the endpoint registry entry with name: " + registryEntry.getEntryName(), e);
        }

        return definitionFile;
    }
}
