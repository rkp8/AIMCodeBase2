/*
Copyright (c) 2011 Tsz-Chiu Au, Peter Stone
University of Texas at Austin
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

3. Neither the name of the University of Texas at Austin nor the names of its
contributors may be used to endorse or promote products derived from this
software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package aim4.im.v2i.RequestHandler;

import aim4.im.v2i.policy.BasePolicy;
import aim4.im.v2i.policy.PolicyCallback;
import aim4.im.v2i.policy.utils.ProposalFilterResult;
import aim4.im.v2i.policy.utils.ReserveParam;
import aim4.msg.i2v.Reject;
import aim4.msg.v2i.Request;
import aim4.sim.StatCollector;

/**
 * The "First Come, First Served" request handler.
 */
public class PBRequestHandler implements RequestHandler {

    /////////////////////////////////
    // PRIVATE FIELDS
    /////////////////////////////////

    /**
     * The base policy
     */
    private PolicyCallback policyCallback = null;


    /////////////////////////////////
    // PUBLIC METHODS
    /////////////////////////////////

    /**
     * Set the base policy call-back.
     *
     * @param policyCallback policy's call-back
     */
    @Override
    public void setPolicyCallback(PolicyCallback policyCallback) {
        this.policyCallback = policyCallback;
    }

    /**
     * Let the request handler to act for a given time period.
     *
     * @param timeStep the time period
     */
    @Override
    public void act(double timeStep) {
        // do nothing
    }

    /**
     * Process the request message.
     *
     * @param msg the request message
     */
    @Override
    public boolean processRequestMsg(Request msg) {
        int vin = msg.getVin();

        // If the vehicle has got a reservation already, reject it.
        if (policyCallback.hasReservation(vin)) {
            policyCallback.sendRejectMsg(vin, msg.getRequestId(), Reject.Reason.CONFIRMED_ANOTHER_REQUEST);
            return false;
        }

        // filter the proposals
        ProposalFilterResult filterResult = BasePolicy.standardProposalsFilter(
                msg.getProposals(), policyCallback.getCurrentTime());
        if (filterResult.isNoProposalLeft()) {
            policyCallback.sendRejectMsg(vin, msg.getRequestId(), filterResult.getReason());
            return false;
        }

        // try to see if reservation is possible for the remaining proposals.
        ReserveParam reserveParam = policyCallback.findReserveParam(msg, filterResult.getProposals());
        if (reserveParam != null) {
            policyCallback.sendConfirmMsg(msg.getRequestId(), reserveParam);
            return true;
        } else {
            policyCallback.sendRejectMsg(vin, msg.getRequestId(), Reject.Reason.NO_CLEAR_PATH);
            return false;
        }
    }

    /**
     * Get the statistic collector.
     *
     * @return the statistic collector
     */
    @Override
    public StatCollector<?> getStatCollector() {
        return null;
    }

}
