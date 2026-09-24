package com.supermarket.salesservice.fixtures.branch;

import com.supermarket.salesservice.client.BranchSummary;
import lombok.experimental.UtilityClass;

@UtilityClass
public class BranchFixtures {

    public static BranchSummary defaultBranch() {
        return new BranchSummary(1L, "Central Branch", "123 Main St", false, true);
    }

    public static BranchSummary inactiveBranch() {
        return new BranchSummary(1L, "Central Branch", "123 Main St", false, false);
    }
}
