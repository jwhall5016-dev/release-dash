package com.releasedash.cmdb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CmdbItemValidatorTest {

    @Test
    void rejectsBlankName() {
        CmdbItem item = validSys();
        item.setName(" ");
        assertThrows(CmdbValidationException.class, () -> CmdbItemValidator.requireValid(item));
    }

    @Test
    void acceptsSysRecord() {
        CmdbItemValidator.requireValid(validSys());
    }

    @Test
    void acceptsGrandchildCiNumber() {
        CmdbItem item = validSys();
        item.setCiNumber("MENTAT-SYS-01.A.01");
        item.setParent("MENTAT-SYS-01.A");
        CmdbItemValidator.requireValid(item);
    }

    private static CmdbItem validSys() {
        CmdbItem item = new CmdbItem();
        item.setName("TALOS");
        item.setCategory("SYS");
        item.setCiNumber("MENTAT-SYS-01.A");
        item.setParent("MENTAT-SYS-01");
        item.setStatus("awaiting approval");
        return item;
    }
}
