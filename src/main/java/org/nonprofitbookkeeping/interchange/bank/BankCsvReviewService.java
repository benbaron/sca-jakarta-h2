package org.nonprofitbookkeeping.interchange.bank;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.BankCsvMappingProfile;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Strict mapped-CSV preview and durable review authority. */
public final class BankCsvReviewService
{
    private final Jpa jpa;
    private final BankCsvParser parser;
    private final BankStatementReviewService reviewService;

    public BankCsvReviewService(Jpa jpa)
    {
        this(jpa, new BankCsvParser(), new BankStatementReviewService(jpa));
    }

    BankCsvReviewService(Jpa jpa, BankCsvParser parser, BankStatementReviewService reviewService)
    {
        this.jpa = java.util.Objects.requireNonNull(jpa, "jpa");
        this.parser = java.util.Objects.requireNonNull(parser, "parser");
        this.reviewService = java.util.Objects.requireNonNull(reviewService, "reviewService");
    }

    public BankCsvReviewPreview preview(
            Path source,
            String companyCode,
            long bankAccountId,
            long profileId)
    {
        LoadedProfile loaded = profile(companyCode, profileId);
        if (loaded.bankAccountId() != bankAccountId)
        {
            throw new IllegalArgumentException("Bank CSV mapping profile belongs to a different configured account.");
        }
        BankCsvParser.ParsedCsv parsed = parser.parse(source, loaded.definition());
        BankStatementReviewPreview review = reviewService.preview(
                source, companyCode, bankAccountId, ignored -> parsed.document());
        return new BankCsvReviewPreview(
                profileId, loaded.portableId(), loaded.hash(), loaded.definition().profileName(),
                review, parsed.originalRows());
    }

    public BankStatementReviewResult commit(
            BankCsvReviewPreview approvedPreview,
            boolean accountIdentityConfirmed,
            String actor)
    {
        if (approvedPreview == null)
        {
            throw new IllegalArgumentException("Approved bank CSV preview is required.");
        }
        LoadedProfile current = profile(
                approvedPreview.review().companyCode(), approvedPreview.profileId());
        if (current.bankAccountId() != approvedPreview.review().bankAccountId()
                || !approvedPreview.profilePortableId().equals(current.portableId())
                || !approvedPreview.profileHash().equals(current.hash()))
        {
            throw new IllegalArgumentException("Bank CSV mapping profile changed after preview; preview it again.");
        }
        return reviewService.commit(
                approvedPreview.review(), accountIdentityConfirmed, actor,
                source -> parser.parse(source, current.definition()).document());
    }

    private LoadedProfile profile(String companyCode, long profileId)
    {
        try (EntityManager em = jpa.em())
        {
            Company company = BankCsvMappingProfileService.company(em, companyCode);
            BankCsvMappingProfile profile = BankCsvMappingProfileService.owned(em, company, profileId);
            if (!profile.isActive())
            {
                throw new IllegalArgumentException("Selected bank CSV mapping profile is inactive.");
            }
            BankCsvMappingProfileDefinition definition =
                    BankCsvMappingProfileDefinition.parse(profile.getMappingJson());
            return new LoadedProfile(
                    profile.getPortableId(), profile.getBankAccount().getId(),
                    definition, sha256(definition.canonicalJson()));
        }
    }

    private static String sha256(String value)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 is required for bank CSV profile identity.", ex);
        }
    }

    private record LoadedProfile(
            java.util.UUID portableId,
            long bankAccountId,
            BankCsvMappingProfileDefinition definition,
            String hash) { }
}
