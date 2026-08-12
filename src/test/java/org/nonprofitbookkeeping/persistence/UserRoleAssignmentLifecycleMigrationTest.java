package org.nonprofitbookkeeping.persistence;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserRoleAssignmentLifecycleMigrationTest
{
    @Test
    void v72AddsRoleLifecycleAndRepeatableAssignmentHistoryConstraints(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("user-role-v72"));
             EntityManager em = jpa.em())
        {
            assertEquals(1L, count(em, """
                    select count(*) from information_schema.columns
                    where table_name = 'APP_ROLE' and column_name = 'IS_ACTIVE'
                    """));
            assertEquals(1L, count(em, """
                    select count(*) from information_schema.columns
                    where table_name = 'USER_COMPANY_ROLE' and column_name = 'START_DATE'
                    """));
            assertEquals(1L, count(em, """
                    select count(*) from information_schema.table_constraints
                    where table_name = 'USER_COMPANY_ROLE'
                      and constraint_name = 'UQ_USER_COMPANY_ROLE_PERIOD'
                    """));
            assertEquals(1L, count(em, """
                    select count(*) from information_schema.table_constraints
                    where table_name = 'USER_COMPANY_ROLE'
                      and constraint_name = 'CK_USER_COMPANY_ROLE_ACTIVE_END'
                    """));

            em.getTransaction().begin();
            em.createNativeQuery("""
                    insert into app_user (username, display_name, is_active)
                    values ('migration-user', 'Migration User', true)
                    """).executeUpdate();
            long userId = ((Number) em.createNativeQuery(
                    "select id from app_user where username = 'migration-user'").getSingleResult()).longValue();
            long companyId = ((Number) em.createNativeQuery(
                    "select id from company where code = 'DEFAULT'").getSingleResult()).longValue();
            long roleId = ((Number) em.createNativeQuery(
                    "select id from app_role where code = 'ADMIN'").getSingleResult()).longValue();
            assertThrows(RuntimeException.class, () -> em.createNativeQuery("""
                    insert into user_company_role
                        (user_id, company_id, role_id, is_active, start_date, end_date)
                    values (:userId, :companyId, :roleId, true, DATE '2026-01-01', DATE '2026-01-31')
                    """)
                    .setParameter("userId", userId)
                    .setParameter("companyId", companyId)
                    .setParameter("roleId", roleId)
                    .executeUpdate());
            em.getTransaction().rollback();
        }
    }

    private static long count(EntityManager em, String sql)
    {
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }
}
