package aim4.im.v2i.policy.utils;

import aim4.msg.i2v.Reject;
import aim4.msg.v2i.Request;

import java.util.List;

/**
 * The result of the standard proposal filter
 */
public class ProposalFilterResult {
    /**
     * The list of proposals
     */
    private List<Request.Proposal> proposals;
    /**
     * The rejection reason
     */
    private Reject.Reason reason;

    /**
     * Create the result of the standard proposal filter
     *
     * @param proposals the list of proposals
     */
    public ProposalFilterResult(List<Request.Proposal> proposals) {
        this.proposals = proposals;
        this.reason = null;
    }

    /**
     * Create the result of the standard proposal filter
     *
     * @param reason the rejection reason
     */
    public ProposalFilterResult(Reject.Reason reason) {
        this.proposals = null;
        this.reason = reason;
    }

    /**
     * Whether any proposal is left.
     *
     * @return whether any proposal is left
     */
    public boolean isNoProposalLeft() {
        return proposals == null;
    }

    /**
     * Get the proposals.
     *
     * @return the proposals
     */
    public List<Request.Proposal> getProposals() {
        return proposals;
    }


    /**
     * Get the rejection reason.
     *
     * @return the rejection reason
     */
    public Reject.Reason getReason() {
        return reason;
    }

}
