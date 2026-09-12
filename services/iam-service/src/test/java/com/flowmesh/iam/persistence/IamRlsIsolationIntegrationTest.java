package com.flowmesh.iam.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowmesh.iam.domain.role.IamRole;
import com.flowmesh.iam.domain.role.UserRole;
import com.flowmesh.iam.domain.tenant.Tenant;
import com.flowmesh.iam.domain.tenant.TenantStatus;
import com.flowmesh.iam.domain.token.RefreshToken;
import com.flowmesh.iam.domain.user.IamUser;
import com.flowmesh.iam.repository.IamRoleRepository;
import com.flowmesh.iam.repository.IamUserRepository;
import com.flowmesh.iam.repository.RefreshTokenRepository;
import com.flowmesh.iam.repository.TenantRepository;
import com.flowmesh.iam.repository.UserRoleRepository;
import com.flowmesh.iam.rls.TenantRlsInitializer;
import com.flowmesh.iam.support.PostgresIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

/**
 * 验证 IAM 用户、角色关系和 Refresh Token 的跨租户读写隔离。
 */
@Transactional
class IamRlsIsolationIntegrationTest extends PostgresIntegrationTest {

    /** 租户持久化访问。 */
    @Autowired
    private TenantRepository tenantRepository;

    /** 用户持久化访问。 */
    @Autowired
    private IamUserRepository userRepository;

    /** 角色持久化访问。 */
    @Autowired
    private IamRoleRepository roleRepository;

    /** 用户角色关系持久化访问。 */
    @Autowired
    private UserRoleRepository userRoleRepository;

    /** Refresh Token 持久化访问。 */
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    /** IAM RLS 上下文初始化器。 */
    @Autowired
    private TenantRlsInitializer tenantRlsInitializer;

    /**
     * 验证切换租户上下文后，不能读取或写入其他租户的用户、角色关系和令牌。
     */
    @Test
    void shouldIsolateIamRowsByTenant() {
        Tenant tenantA = tenantRepository.saveAndFlush(
                new Tenant("tenant-a-rls", "租户 A", TenantStatus.ACTIVE)
        );
        Tenant tenantB = tenantRepository.saveAndFlush(
                new Tenant("tenant-b-rls", "租户 B", TenantStatus.ACTIVE)
        );

        tenantRlsInitializer.initialize(tenantA.getId());
        IamUser userA = userRepository.saveAndFlush(
                new IamUser(tenantA, "reviewer-a", "password-hash", "租户 A 审批人")
        );
        IamRole role = roleRepository.saveAndFlush(
                new IamRole("RLS_REVIEWER", "RLS 审批人", "RLS 测试角色")
        );
        userRoleRepository.saveAndFlush(new UserRole(userA, role));
        refreshTokenRepository.saveAndFlush(
                new RefreshToken(userA, "tenant-a-token-hash", Instant.now().plusSeconds(3600))
        );

        tenantRlsInitializer.initialize(tenantB.getId());
        assertThat(userRepository.findById(userA.getId())).isEmpty();
        assertThat(userRoleRepository.findAllByUser_Id(userA.getId())).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash("tenant-a-token-hash")).isEmpty();

        assertThatThrownBy(() -> userRepository.insert(
                new IamUser(tenantA, "forbidden-user", "password-hash", "越权用户")
        )).isInstanceOf(DataAccessException.class);
    }
}
