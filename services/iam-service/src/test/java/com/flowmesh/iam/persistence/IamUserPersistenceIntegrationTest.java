package com.flowmesh.iam.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmesh.iam.domain.tenant.Tenant;
import com.flowmesh.iam.domain.tenant.TenantStatus;
import com.flowmesh.iam.domain.user.IamUser;
import com.flowmesh.iam.repository.IamUserRepository;
import com.flowmesh.iam.repository.TenantRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 验证租户、用户实体和派生查询与 IAM 数据库结构能够协同工作。
 */
@SpringBootTest
@Transactional
class IamUserPersistenceIntegrationTest {

    /** 租户持久化访问。 */
    @Autowired
    private TenantRepository tenantRepository;

    /** 用户持久化访问。 */
    @Autowired
    private IamUserRepository iamUserRepository;

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
}
