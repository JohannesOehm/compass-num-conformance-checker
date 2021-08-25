package science.numcompass.conformance.fhir.chain

import ca.uhn.fhir.context.support.ConceptValidationOptions
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.context.support.ValidationSupportContext
import ca.uhn.fhir.context.support.ValueSetExpansionOptions
import ca.uhn.fhir.jpa.api.config.DaoConfig
import ca.uhn.fhir.jpa.term.IValueSetConceptAccumulator
import ca.uhn.fhir.jpa.term.TermReadSvcR4
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.ValueSet
import science.numcompass.conformance.fhir.chain.ValidationChain.IValidator

/**
 * Try to check if a system-code pair represents a SNOMED expression that the TerminologyService
 * will incorrectly classify as invalid. This is used for skipping validation of such terms to
 * avoid spurious errors.
 *
 * Note that this function is not meant to evaluate whether a given code string is actually
 * a *valid* SNOMED code 9or valid FHIR code in general). It will only check whether it appears
 * to be a valid SNOMED expression code that would incorrectly fail validation. Hence, the check
 * will actually let through e.g. valid SNOMED expressions with leading spaces. The reason is that
 * such codes can *correctly* be flagged as invalid by the validator because FHIR codes can never
 * have a leading space.
 */
internal fun isSnomedCodeThatShouldNotBeValidated(theCodeSystem: String?, theCode: String?): Boolean {
    // Check if this is declared as a SNOMED code
    if (theCode == null || theCodeSystem?.startsWith("http://snomed.info/sct") != true) return false
    /* We classify any integer followed by ":", "|", or "+" (possibly with spaces)
    as a SNOMED expression */
    val isASnomedExpression = Regex("^\\d+\\s*[\\:\\|\\+]")
    return isASnomedExpression.containsMatchIn(theCode)
}

/**
 * This server extends [TermReadSvcR4] to prevent missing code system errors.
 */
open class TerminologyService(daoConfig: DaoConfig) : IValidator, TermReadSvcR4() {

    /**
     * Default options to prevent errors caused by missing code systems
     * and expanding value sets with more than 1000 codes.
     */
    private val defaultExpansionOptions = ValueSetExpansionOptions().apply {
        count = daoConfig.maximumExpansionSize
        isFailOnMissingCodeSystem = false
    }

    override fun expandValueSet(
        context: ValidationSupportContext?,
        options: ValueSetExpansionOptions?,
        valueSet: IBaseResource
    ): IValidationSupport.ValueSetExpansionOutcome? {
        return super.expandValueSet(context, options ?: defaultExpansionOptions, valueSet)
    }

    override fun expandValueSet(
        options: ValueSetExpansionOptions?,
        valueSet: ValueSet?,
        accumulator: IValueSetConceptAccumulator?
    ) {
        super.expandValueSet(options ?: defaultExpansionOptions, valueSet, accumulator)
    }

    /**
     * This override catches post-coordinates (composite) SNOMED codes that cannot be validated and
     * returns a warning for them (to avoiding spurious errors being returned from code validation).
     */
    override fun validateCode(
        theValidationSupportContext: ValidationSupportContext?,
        theOptions: ConceptValidationOptions?,
        theCodeSystem: String?,
        theCode: String?,
        theDisplay: String?,
        theValueSetUrl: String?
    ): IValidationSupport.CodeValidationResult? {

        if (isSnomedCodeThatShouldNotBeValidated(theCodeSystem, theCode))
            return IValidationSupport.CodeValidationResult()
                .setSeverity(IValidationSupport.IssueSeverity.WARNING)
                .setCodeSystemName(theCodeSystem)
                .setCode(theCode)
                .setMessage("Validation of SNOMED expressions are not supported - did not validate $theCode")

        return super<TermReadSvcR4>.validateCode(
            theValidationSupportContext,
            theOptions,
            theCodeSystem,
            theCode,
            theDisplay,
            theValueSetUrl
        )
    }

}
