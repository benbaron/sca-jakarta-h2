package org.nonprofitbookkeeping.ui;

/** Executes a typed database-recovery command in the owning workspace shell. */
@FunctionalInterface
interface DatabaseRecoveryCommandHandler
{
    void execute(DatabaseRecoveryCommand command);
}
