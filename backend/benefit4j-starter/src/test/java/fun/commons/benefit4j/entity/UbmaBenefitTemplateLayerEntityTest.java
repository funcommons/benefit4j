package fun.commons.benefit4j.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UbmaBenefitTemplateLayerEntityTest {
    @Test
    void templateEntitiesUseIndependentTables() throws NoSuchFieldException {
        assertThat(UbmpBenefitTmplSet.class.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class).value())
                .isEqualTo("ubmp_benefit_tmpl_set");
        assertThat(UbmpBenefitTmplItem.class.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class).value())
                .isEqualTo("ubmp_benefit_tmpl_item");
        assertThat(UbmpBenefitTmplRef.class.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class).value())
                .isEqualTo("ubmp_benefit_tmpl_ref");
    }
}