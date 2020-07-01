package org.wso2.carbon.apimgt.rest.api.admin.v1.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.simple.JSONObject;

import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.wso2.carbon.apimgt.api.APIAdmin;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.ExceptionCodes;
import org.wso2.carbon.apimgt.impl.APIAdminImpl;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.APIMRegistryService;
import org.wso2.carbon.apimgt.impl.APIMRegistryServiceImpl;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.rest.api.admin.v1.*;
import org.wso2.carbon.apimgt.rest.api.admin.v1.dto.*;

import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.MessageContext;

import org.wso2.carbon.apimgt.rest.api.admin.v1.dto.ErrorDTO;
import org.wso2.carbon.apimgt.rest.api.admin.v1.dto.ScopeSettingsDTO;
import org.wso2.carbon.apimgt.rest.api.admin.v1.utils.mappings.SystemScopesMappingUtil;
import org.wso2.carbon.apimgt.rest.api.util.utils.RestApiUtil;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

import java.util.Base64;
import java.util.Map;

import javax.cache.Caching;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;


public class SystemScopesApiServiceImpl implements SystemScopesApiService {

    private static final Log log = LogFactory.getLog(SystemScopesApiServiceImpl.class);

    public Response systemScopesScopeNameGet(String scope, String username, MessageContext messageContext) throws APIManagementException{
        ScopeSettingsDTO scopeSettingsDTO = new ScopeSettingsDTO();
        APIAdmin apiAdmin = new APIAdminImpl();
        String decodedScope = new String(Base64.getDecoder().decode(scope));
        boolean existence ;

            if (username == null) {
                existence = apiAdmin.isScopeExists(RestApiUtil.getLoggedInUsername(), decodedScope);
                if (existence) {
                    scopeSettingsDTO.setName(decodedScope);
                } else {
                    throw new APIManagementException("Scope Not Found.  Scope : " + decodedScope +
                            ExceptionCodes.SCOPE_NOT_FOUND);
                }
            } else {
                existence = apiAdmin.isScopeExistsForUser(username, decodedScope);
                if (existence) {
                    scopeSettingsDTO.setName(decodedScope);
                } else {
                    throw new APIManagementException("User does not have  scope. Username :" + username + " Scope : "
                            + decodedScope + ExceptionCodes.SCOPE_NOT_FOUND_FOR_USER);
                }
            }
        return Response.ok().entity(scopeSettingsDTO).build();
    }

    public Response systemScopesGet(MessageContext messageContext) throws APIManagementException {
        try {
            Map<String, String> scopeRoleMapping = APIUtil.getRESTAPIScopesForTenant(MultitenantUtils
                    .getTenantDomain(RestApiUtil.getLoggedInUsername()));
            ScopeListDTO scopeListDTO = SystemScopesMappingUtil.fromScopeListToScopeListDTO(scopeRoleMapping);
            return Response.ok().entity(scopeListDTO).build();
        } catch (APIManagementException e) {
            String errorMessage = "Error when getting the list of scopes-role mapping.";
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        }
        return null;
    }

    public Response updateRolesForScope(ScopeListDTO body, MessageContext messageContext)
            throws APIManagementException {
        String tenantDomain = MultitenantUtils.getTenantDomain(RestApiUtil.getLoggedInUsername());
        //read from tenant-conf.json
        JsonObject existingTenantConfObject = new JsonObject();
        try {
            APIMRegistryService apimRegistryService = new APIMRegistryServiceImpl();
            String existingTenantConf = apimRegistryService.getConfigRegistryResourceContent(tenantDomain,
                    APIConstants.API_TENANT_CONF_LOCATION);
            existingTenantConfObject = new JsonParser().parse(existingTenantConf).getAsJsonObject();
        } catch (RegistryException | UserStoreException e) {
            APIUtil.handleException("Couldn't read tenant configuration from tenant registry", e);
        }
        JSONObject responseJson = SystemScopesMappingUtil.createJsonObjectOfScopeMapping(body);
        //Here we are removing RESTAPIScopes from the existing tenant-conf
        // Adding new RESTAPIScopes to the existing tenant-conf.
        existingTenantConfObject.remove(APIConstants.REST_API_SCOPES_CONFIG);
        JsonElement jsonElement = new JsonParser().parse(responseJson.toJSONString());
        existingTenantConfObject.add(APIConstants.REST_API_SCOPES_CONFIG, jsonElement);
        try {
            ObjectMapper mapper = new ObjectMapper();
            String formattedTenantConf = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(existingTenantConfObject.toString());
            APIUtil.updateTenantConf(existingTenantConfObject.toString(), tenantDomain);
            if (log.isDebugEnabled()) {
                log.debug("Finalized tenant-conf.json: " + formattedTenantConf);
            }
        } catch (JsonProcessingException e) {
            throw new APIManagementException("Error while formatting tenant-conf.json of tenant ");
        }
        // Invalidate Cache
        Caching.getCacheManager(APIConstants.API_MANAGER_CACHE_MANAGER)
                .getCache(APIConstants.REST_API_SCOPE_CACHE)
                .put(tenantDomain, null);
        Map<String, String> scopeRoleMapping = APIUtil.getRESTAPIScopesForTenant(MultitenantUtils
                .getTenantDomain(RestApiUtil.getLoggedInUsername()));
        ScopeListDTO scopeListDTO = SystemScopesMappingUtil.fromScopeListToScopeListDTO(scopeRoleMapping);
        return Response.ok().entity(scopeListDTO).build();
    }
}
