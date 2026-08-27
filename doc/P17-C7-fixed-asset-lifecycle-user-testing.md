# P17-C7 fixed-asset lifecycle owner testing

Use a disposable company/database with at least one active fixed asset and valid fixed-asset, accumulated-depreciation, depreciation-expense, and fund references.

1. Open **Asset Register**. Confirm Status is read-only and the toolbar exposes **Deactivate Asset** and **Reactivate Asset**; there is no generic Delete action.
2. Select an ACTIVE asset, enter a lifecycle actor and reason, choose **Deactivate Asset**, confirm the action, and verify the same stable asset remains visible as INACTIVE with all existing depreciation/lifecycle history retained.
3. With that asset INACTIVE, verify monthly depreciation and **Record Lifecycle Event...** cannot proceed until the asset is reactivated.
4. Enter actor/reason and choose **Reactivate Asset**. Verify the same asset returns ACTIVE and can again enter governed depreciation or Sale/Retirement/Impairment workflows.
5. Edit ordinary metadata on ACTIVE and INACTIVE assets and save. Confirm those saves do not change lifecycle status.
6. Record a Sale or Retirement on an ACTIVE disposable asset. Confirm status becomes DISPOSED, ordinary asset editing and Activate/Deactivate are unavailable, and the row/history remain visible.
7. Reverse that Sale/Retirement through **Reverse Selected Lifecycle Event**. Confirm canonical reversal accounting is created and the asset returns to its prior lifecycle status.
8. Open **Audit History** and confirm ACTIVE/INACTIVE transitions appear as `FIXED_ASSET_STATUS_CHANGED` with the factual actor, before/after status, and entered reason.
9. Restart/reopen the company and confirm retained statuses, asset history, lifecycle history, and table/divider state remain authoritative from H2.
