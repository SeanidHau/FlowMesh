package com.flowmesh.iam.persistence;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.flowmesh.iam.support.PostgresIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * 验证租户、用户实体和派生查询与 IAM 数据库结构能够协同工作。
 */
@Transactional
class IamUserPersistenceIntegrationTest extends PostgresIntegrationTest {

    /** 租户持久化访问。 */
    @Autowired
    private TenantRepository tenantRepository;

    /** 用户持久化访问。 */
    @Autowired
    private IamUserRepository iamUserRepository;

    /** IAM 角色持久化访问。 */
    @Autowired
    private IamRoleRepository iamRoleRepository;

    /** 用户角色关系持久化访问。 */
    @Autowired
    private UserRoleRepository userRoleRepository;

    /** 刷新令牌持久化访问。 */
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    /**
     * 验证用户创建时会归一化用户名，并可按租户和用户名查询。
     */
    @Test
    void shouldPersistAndFindUserWithinTenant() {
        String tenantId = "tenant-" + UUID.randomUUID();
        Tenant tenant = tenantRepository.saveAndFlush(
                new Tenant(tenantId, "测试租户", TenantStatus.ACTIVE)
        );

        IamUser user = iamUserRepository.saveAndFlush(
                new IamUser(tenant, "  Admin.User  ", "password-hash", "管理员")
        );

        assertThat(user.getId()).isNotNull();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
        assertThat(user.getUsername()).isEqualTo("admin.user");
        assertThat(iamUserRepository.existsByTenant_IdAndUsername(tenantId, "admin.user"))
                .isTrue();
        assertThat(iamUserRepository.findByTenant_IdAndUsername(tenantId, "admin.user"))
                .contains(user);
    }

    /**
     * 验证用户角色关联使用复合主键持久化，并可按用户和角色代码查询。
     */
    @Test
    void shouldPersistAndFindUserRoleAssignment() {
        String tenantId = "tenant-" + UUID.randomUUID();
        Tenant tenant = tenantRepository.saveAndFlush(
                new Tenant(tenantId, "测试租户", TenantStatus.ACTIVE)
        );
        IamUser user = iamUserRepository.saveAndFlush(
                new IamUser(tenant, "reviewer", "password-hash", "审批人")
        );
        IamRole role = iamRoleRepository.saveAndFlush(
                new IamRole(" procurement_reviewer ", "采购审批人", "执行采购初审")
        );

        UserRole userRole = userRoleRepository.saveAndFlush(new UserRole(user, role));

        assertThat(userRole.getAssignedAt()).isNotNull();
        assertThat(userRole.getId().getUserId()).isEqualTo(user.getId());
        assertThat(userRole.getId().getRoleId()).isEqualTo(role.getId());
        assertThat(userRoleRepository.existsByUser_IdAndRole_Code(
                user.getId(),
                "PROCUREMENT_REVIEWER"
        )).isTrue();
        assertThat(userRoleRepository.findAllByUser_Id(user.getId()))
                .containsExactly(userRole);
    }

    /**
     * 验证刷新令牌支持按哈希锁定查询，并在轮换后保持旧令牌不可用。
     */
    @Test
    void shouldRotateRefreshToken() {
        Tenant tenant = tenantRepository.saveAndFlush(
                new Tenant("tenant-" + UUID.randomUUID(), "测试租户", TenantStatus.ACTIVE)
        );
        IamUser user = iamUserRepository.saveAndFlush(
                new IamUser(tenant, "operator", "password-hash", "运营人员")
        );
        Instant now = Instant.now();
        RefreshToken oldToken = refreshTokenRepository.saveAndFlush(
                new RefreshToken(user, "old-token-hash", now.plusSeconds(3600))
        );
        RefreshToken replacementToken = refreshTokenRepository.saveAndFlush(
                new RefreshToken(user, "replacement-token-hash", now.plusSeconds(3600))
        );

        oldToken.replaceWith(replacementToken, now);
        refreshTokenRepository.updateRotation(oldToken);

        assertThat(oldToken.isActiveAt(now.plusSeconds(1))).isFalse();
        assertThat(oldToken.getReplacedByToken()).isEqualTo(replacementToken);
        assertThat(refreshTokenRepository.findByTokenHashForUpdate("old-token-hash"))
                .contains(oldToken);
        assertThat(refreshTokenRepository.findAllByUser_IdAndRevokedAtIsNull(user.getId()))
                .containsExactly(replacementToken);
    }
}
