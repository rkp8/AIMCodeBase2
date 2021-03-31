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
package aim4.im.v2i.policy;

import aim4.config.Debug;
import aim4.im.TrackModel;
import aim4.im.v2i.RequestHandler.PBRequestHandler;
import aim4.im.v2i.RequestHandler.RequestHandler;
import aim4.im.v2i.V2IManager;
import aim4.im.v2i.V2IManagerCallback;
import aim4.im.v2i.policy.utils.ProposalFilterResult;
import aim4.im.v2i.policy.utils.ReservationRecord;
import aim4.im.v2i.policy.utils.ReserveParam;
import aim4.im.v2i.reservation.AczManager;
import aim4.im.v2i.reservation.ReservationGrid;
import aim4.im.v2i.reservation.ReservationGridManager;
import aim4.im.v2i.reservation.ReservationGridManager.Plan;
import aim4.map.lane.Lane;
import aim4.msg.i2v.Confirm;
import aim4.msg.i2v.Reject;
import aim4.msg.i2v.Reject.Reason;
import aim4.msg.v2i.*;
import aim4.msg.v2i.Request.Proposal;
import aim4.sim.StatCollector;
import aim4.util.HashMapRegistry;
import aim4.util.Registry;
import aim4.vehicle.VehicleUtil;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * The base policy.
 */
public final class PriorityBasedPolicy implements Policy, BasePolicyCallback, PolicyCallback {
    //private static Logger logger = LoggerFactory.getLogger(PriorityBasedPolicy.class);

    /////////////////////////////////
    // CONSTANTS
    /////////////////////////////////

    /**
     * The maximum amount of time, in seconds, to let a vehicle arrive early.
     * {@value} seconds.
     */
    private static final double EARLY_ERROR = 0.01;
    /**
     * The maximum amount of time, in seconds to let a vehicle arrive late.
     * {@value} seconds.
     */
    private static final double LATE_ERROR = 0.01;


    /////////////////////////////////
    // NESTED CLASSES
    /////////////////////////////////


    /////////////////////////////////
    // PUBLIC STATIC METHODS
    /////////////////////////////////

    /**
     * Remove the proposals that are either too early or too late.
     *
     * @param proposals   the list of proposals
     * @param currentTime the current time
     * @return the proposal filter result
     */
    public static ProposalFilterResult standardProposalsFilter(
            List<Proposal> proposals,
            double currentTime) {
        // copy the proposals to a list first.
        List<Proposal> myProposals =
                new LinkedList<Proposal>(proposals);

        // Remove proposals whose arrival time is smaller than or equal to the
        // the current time.
        PriorityBasedPolicy.removeProposalWithLateArrivalTime(myProposals, currentTime);
        if (myProposals.isEmpty()) {
            return new ProposalFilterResult(Reason.ARRIVAL_TIME_TOO_LATE);
        }
        // Check to see if not all of the arrival times in this reservation
        // request are too far in the future
        PriorityBasedPolicy.removeProposalWithLargeArrivalTime(
                myProposals, currentTime + V2IManager.MAXIMUM_FUTURE_RESERVATION_TIME);
        if (myProposals.isEmpty()) {
            return new ProposalFilterResult(Reason.ARRIVAL_TIME_TOO_LARGE);
        }
        // return the remaining proposals
        return new ProposalFilterResult(myProposals);
    }

    /**
     * Remove proposals whose arrival time is small than or equal to the
     * current time.
     *
     * @param proposals   a list of proposals
     * @param currentTime the current time
     */
    private static void removeProposalWithLateArrivalTime(
            List<Proposal> proposals,
            double currentTime) {
        for (Iterator<Proposal> tpIter = proposals.listIterator();
             tpIter.hasNext(); ) {
            Proposal prop = tpIter.next();
            // If this one is in the past
            if (prop.getArrivalTime() <= currentTime) {
                tpIter.remove();
            }
        }
    }

    /**
     * Remove proposals whose arrival time is larger than the current time plus
     * the maximum future reservation time.
     *
     * @param proposals  a set of proposals
     * @param futureTime the future arrival time beyond which a proposal is
     *                   invalid.
     */
    private static void removeProposalWithLargeArrivalTime(
            List<Proposal> proposals,
            double futureTime) {
        for (Iterator<Proposal> tpIter = proposals.listIterator();
             tpIter.hasNext(); ) {
            Proposal prop = tpIter.next();
            // If this one is in the past
            if (prop.getArrivalTime() > futureTime) {
                tpIter.remove();
            }
        }
    }


    /////////////////////////////////
    // PRIVATE FIELDS
    /////////////////////////////////

    /**
     * The V2IManager of which this Policy is a part.
     */
    protected V2IManagerCallback im;

    /**
     * The proposal handler.
     */
    private PBRequestHandler requestHandler;

    /**
     * The confirm message registry
     */
    private Registry<ReservationRecord> reservationRecordRegistry =
            new HashMapRegistry<ReservationRecord>();

    /**
     * A mapping from VIN numbers to reservation Id
     */
    private Map<Integer, Integer> vinToReservationId =
            new HashMap<Integer, Integer>();

    /**
     * The statistic collector
     */
    private StatCollector<PriorityBasedPolicy> statCollector;


    /**
     * A mapping from laneid to VINs in this lane
     */
    private HashMap<Integer, HashSet<Integer>> laneIdToVins = new HashMap<Integer, HashSet<Integer>>();

    /**
     * A mapping from VIN to its V2IMessage.
     * assume every VIN has only one message at one time
     */
    private HashMap<Integer, Request> vinToMessage = new HashMap<Integer, Request>();
    /////////////////////////////////
    // CLASS CONSTRUCTORS
    /////////////////////////////////

    /**
     * Create a new base policy for a given V2IManagerCallback (implemented by
     * a V2IManager) and a request handler.
     *
     * @param im             the V2IManagerCallback for which this FCFSPolicy is
     *                       being created
     * @param requestHandler the request handler
     */
    public PriorityBasedPolicy(V2IManagerCallback im, PBRequestHandler requestHandler) {
        this(im, requestHandler, null);
    }

    /**
     * Create a new base policy for a given V2IManagerCallback (implemented by
     * a V2IManager) and a request handler.
     *
     * @param im             the V2IManagerCallback for which this FCFSPolicy is
     *                       being created
     * @param requestHandler the request handler
     * @param statCollector  the statistic collector
     */
    public PriorityBasedPolicy(V2IManagerCallback im,
                               PBRequestHandler requestHandler,
                               StatCollector<PriorityBasedPolicy> statCollector) {
        this.im = im;
        this.statCollector = statCollector;
        setRequestHandler(requestHandler);

        for (Lane lane : this.im.getIntersection().getLanes()) {
            this.laneIdToVins.put(lane.getId(), new HashSet<Integer>());
        }
    }


    /////////////////////////////////
    // PUBLIC METHODS
    /////////////////////////////////

    /**
     * {@inheritDoc}
     */
    @Override
    public void setV2IManagerCallback(V2IManagerCallback im) {
        this.im = im;
    }

    /**
     * Get the request handler.
     *
     * @return the request handler.
     */
    public RequestHandler getRequestHandler() {
        return requestHandler;
    }

    /**
     * Set the request handler.
     *
     * @param RequestHandler the request handler.
     */
    public void setRequestHandler(PBRequestHandler RequestHandler) {
        this.requestHandler = RequestHandler;
        requestHandler.setPolicyCallback(this);
    }


    /////////////////////////////////
    // PUBLIC METHODS
    /////////////////////////////////

    /**
     * {@inheritDoc}
     */
    @Override
    public void act(double timeStep) {
        requestHandler.act(timeStep);
        if (statCollector != null) statCollector.collect(this);
    }


    /////////////////////////////////
    // PUBLIC METHODS
    /////////////////////////////////

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendConfirmMsg(int latestRequestId,
                               ReserveParam reserveParam) {
        int vin = reserveParam.getVin();

        // make sure that there is no other confirm message is in effect
        // when the request handler sends the confirm message.
        assert !vinToReservationId.containsKey(vin);
        // actually make the reservation
        Integer gridTicket = im.getReservationGridManager().accept(reserveParam.getGridPlan());
        Integer aczTicket = reserveParam.getAczManager().accept(reserveParam.getAczPlan());
        assert gridTicket == vin;
        assert aczTicket == vin;

        // send the confirm message
        int reservationId = reservationRecordRegistry.getNewId();
        Confirm confirmMsg = new Confirm(im.getId(),
                vin,
                reservationId,
                latestRequestId,
                reserveParam.getSuccessfulProposal().getArrivalTime(),
                EARLY_ERROR, LATE_ERROR,
                reserveParam.getSuccessfulProposal().getArrivalVelocity(),
                reserveParam.getSuccessfulProposal().getArrivalLaneID(),
                reserveParam.getSuccessfulProposal().getDepartureLaneID(),
                im.getACZ(reserveParam.getSuccessfulProposal().getDepartureLaneID()).getMaxSize(),
                reserveParam.getGridPlan().getAccelerationProfile());
        im.sendI2VMessage(confirmMsg);

        // book keeping
        ReservationRecord r = new ReservationRecord(
                vin,
                reserveParam.getSuccessfulProposal().getDepartureLaneID());
        reservationRecordRegistry.set(reservationId, r);
        vinToReservationId.put(vin, reservationId);

        // debug
        if (Debug.isTargetVIN(vin)) {
            System.err.printf("workinglist = %s\n",
                    reserveParam.getGridPlan().getWorkingList());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendRejectMsg(int vin, int latestRequestId, Reason reason) {
        im.sendI2VMessage(new Reject(im.getId(),
                vin,
                latestRequestId,
                im.getCurrentTime(), // can re-send request
                // immediately
                reason));
    }

    /**
     * {@inheritDoc}
     * @return
     */
    @Override
    public ReserveParam findReserveParam(Request msg,
                                         List<Proposal> proposals) {
        int vin = msg.getVin();

        // Okay, now let's actually try some of these proposals
        Proposal successfulProposal = null;
        Plan gridPlan = null;
        AczManager aczManager = null;
        AczManager.Plan aczPlan = null;

        for (Proposal proposal : proposals) {
            ReservationGridManager.Query gridQuery =
                    new ReservationGridManager.Query(vin,
                            proposal.getArrivalTime(),
                            proposal.getArrivalVelocity(),
                            proposal.getArrivalLaneID(),
                            proposal.getDepartureLaneID(),
                            msg.getSpec(),
                            proposal.getMaximumTurnVelocity(),
                            true);
            gridPlan = im.getReservationGridManager().query(gridQuery);
            if (gridPlan != null) {
                double stopDist =
                        VehicleUtil.calcDistanceToStop(gridPlan.getExitVelocity(),
                                msg.getSpec().getMaxDeceleration());

                aczManager = im.getAczManager(proposal.getDepartureLaneID());
                if (aczManager == null) {
                    System.err.printf("FCFSPolicy::processRequestMsg(): " +
                            "aczManager should not be null.\n");
                    System.err.printf("proposal.getDepartureLaneID() = %d\n",
                            proposal.getDepartureLaneID());
                    aczPlan = null;
                } else {
                    AczManager.Query aczQuery =
                            new AczManager.Query(vin,
                                    gridPlan.getExitTime(),
                                    gridPlan.getExitVelocity(),
                                    msg.getSpec().getLength(),
                                    stopDist);
                    aczPlan = aczManager.query(aczQuery);
                    if (aczPlan != null) {
                        successfulProposal = proposal;  // reservation succeeds!
                        break;
                    }
                }
            }
        }

        if (successfulProposal != null) {
            return new ReserveParam(vin, successfulProposal, gridPlan, aczManager,
                    aczPlan);
        } else {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getCurrentTime() {
        return im.getCurrentTime();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReservationGrid getReservationGrid() {
        return im.getReservationGrid();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasReservation(int vin) {
        return vinToReservationId.containsKey(vin);
    }


    /////////////////////////////////
    // PUBLIC METHODS
    /////////////////////////////////

    /**
     * Process a V2I message
     *
     * @param msg the V2I message
     */
    @Override
    public void processV2IMessage(V2IMessage msg) {
        if (msg instanceof Request) {
            List<Proposal> proposals = ((Request) msg).getProposals();
            assert !proposals.isEmpty();
            int laneId = proposals.get(0).getArrivalLaneID();
            laneIdToVins.get(laneId).add(msg.getVin());
            vinToMessage.put(msg.getVin(), (Request) msg);
        } else if (msg instanceof Cancel) {
            processCancelMsg((Cancel) msg);
        } else if (msg instanceof Done) {
            processDoneMsg((Done) msg);
        } else if (msg instanceof Away) {
            processAwayMsg((Away) msg);
        } else {
            throw new RuntimeException("Unhandled message type: " + msg);
        }
    }

    /**
     * This is where priority comes into play:
     */
    public void processV2IMessageDone() {
        // in case no message yet
//        if (vinToMessage.isEmpty() || laneIdToVins.isEmpty()) {
//            System.out.println("\nvinToMessage.isEmpty() || laneIdToVins.isEmpty()\n");
//            return;
//        }
        while (!vinToMessage.isEmpty()) {
            // store closest vin in each lane
            List<Integer> closestVinInLane = new ArrayList<Integer>();
            // store each lane's priority
            List<Double> lanePriority = new ArrayList<Double>();
            for (int i = 0; i < laneIdToVins.size(); ++i) {
                closestVinInLane.add(-1);

                if(i==1){
                    lanePriority.add(10000000.0);

                }
                else
                    lanePriority.add(-1000000.0);

            }

            // find closest vin in each lane
            for (Integer laneId : laneIdToVins.keySet()) {
                if (laneIdToVins.get(laneId).isEmpty()) {
                    // in case no vin in this lane
                    continue;
                }

                // closest vin in this lane
                int closestVin = -1;
                // and its distance
                double closestDistance = 1e9;

                for (Integer vin : laneIdToVins.get(laneId)) {
                    Request msg = vinToMessage.get(vin);
                    if (msg == null) {
                        // in case..should never arrive here
                        continue;
                    }
                    // get distance from one of its proposal
                    Proposal proposal = msg.getProposals().get(0);
                    double reservationDistance = (proposal.getArrivalTime() - getCurrentTime()) * proposal.getArrivalVelocity();
                    if (reservationDistance < closestDistance) {
                        closestDistance = reservationDistance;
                        closestVin = vin;
                    }
                    // accumulate the lane priority
                    lanePriority.set(laneId, lanePriority.get(laneId)); //+ msg.getPriority());
                }
                lanePriority.set(laneId, lanePriority.get(laneId) );/// (closestDistance + 0.1));
                closestVinInLane.set(laneId, closestVin);
            }

            System.out.println("lane_priority: {}"+ lanePriority);


            // logger.debug("lane_priority: {}", lanePriority);

            int theVin = -1;
            int theLaneId = -1;
            Request theMessage = null;
            double highestPriority = -1.0;

           System.out.println("laneIds: " + laneIdToVins.size());
            for (int laneId = 0; laneId < laneIdToVins.size(); ++laneId) {
                if (lanePriority.get(laneId) > highestPriority) {
                    highestPriority = lanePriority.get(laneId);
                    theLaneId = laneId;
                    theVin = closestVinInLane.get(laneId);
                    theMessage = vinToMessage.get(theVin);
                }
            }

            for (Integer vin : closestVinInLane) {
                if (vin == -1) {
                    continue;
                }
                Request msg = vinToMessage.get(vin);
                if (msg != theMessage) {
                    msg.setPriority(msg.getPriority() << 1);
                    vinToMessage.put(vin, msg);
                }
            }

            System.out.println("laneIdToVins: {}" + laneIdToVins);
            System.out.println("vinToMessage: {}" + vinToMessage);
            System.out.println("theMessage: {}"+ theMessage);


            // logger.debug("laneIdToVins: {}", laneIdToVins);
           // logger.debug("vinToMessage: {}", vinToMessage);
           // logger.debug("theMessage: {}", theMessage);

            assert theMessage != null;
            requestHandler.processRequestMsg(theMessage);
            laneIdToVins.get(theLaneId).remove(theVin);
            vinToMessage.remove(theVin);
        }
        cleanUp();
    }

    private void cleanUp() {
        laneIdToVins = new HashMap<Integer, HashSet<Integer>>();
        vinToMessage = new HashMap<Integer, Request>();

        for (Lane lane : this.im.getIntersection().getLanes()) {
            this.laneIdToVins.put(lane.getId(), new HashSet<Integer>());
        }
    }

    /**
     * Submit a cancel message to the policy.
     *
     * @param msg the cancel message
     */
    public void processCancelMsg(Cancel msg) {
        ReservationRecord r = reservationRecordRegistry.get(msg.getReservationID());
        if (r != null) {
            int vin = r.getVin();    // don't use the VIN in msg in case
            // vin != msg.getVin()
            if (vin != msg.getVin()) {
                System.err.printf("BasePolicy::processCancelMsg(): " +
                        "The VIN of the message is different from the VIN " +
                        "on the record.\n");
            }
            // release the resources
            im.getReservationGridManager().cancel(vin);
            im.getAczManager(r.getAczLaneId()).cancel(vin);
            // remove the reservation record
            reservationRecordRegistry.setNull(msg.getReservationID());
            vinToReservationId.remove(vin);
        } else {
            System.err.print("BasePolicy::processCancelMsg(): " +
                    "record not found\n");
        }
    }


    /**
     * Submit a done message to the policy.
     *
     * @param msg the done message
     */
    public void processDoneMsg(Done msg) {
        ReservationRecord r = reservationRecordRegistry.get(msg.getReservationID());
        if (r != null) {
            int vin = r.getVin();   // don't use the VIN in msg.
            if (vin != msg.getVin()) {
                System.err.print("BasePolicy::processCancelMsg(): " +
                        "The VIN of the message is different from the VIN " +
                        "on the record.\n");
            }
            // do nothing with the done message since the reservation grid is
            // automatically cleaned.
        } else {
            System.err.print("BasePolicy::processDoneMsg(): " +
                    "record not found");
        }
    }


    /**
     * Submit an away message to the policy.
     *
     * @param msg the away message
     */
    public void processAwayMsg(Away msg) {
        ReservationRecord r = reservationRecordRegistry.get(msg.getReservationID());
        if (r != null) {
            int vin = r.getVin();  // don't use the VIN in msg.
            if (vin != msg.getVin()) {
                System.err.printf("BasePolicy::processCancelMsg(): " +
                        "The VIN of the message is different from the VIN " +
                        "on the record.\n");
            }
            // clear the reservation in ACZ.
            im.getACZ(r.getAczLaneId()).away(vin);
            // remove the reservation record
            reservationRecordRegistry.setNull(msg.getReservationID());
            vinToReservationId.remove(vin);
        } else {
            System.err.printf("BasePolicy::processAwayMsg(): record not found");
        }
    }


    /////////////////////////////////
    // PUBLIC METHODS
    /////////////////////////////////

    // statistics

    /**
     * {@inheritDoc}
     */
    @Override
    public StatCollector<PriorityBasedPolicy> getStatCollector() {
        return statCollector;
    }

    // TODO: remove this function later.

    /**
     * {@inheritDoc}
     */
    @Override
    public TrackModel getTrackMode() {
        return im.getTrackModel();
    }


}
