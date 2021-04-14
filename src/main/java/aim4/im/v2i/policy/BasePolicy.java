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
import aim4.im.v2i.RequestHandler.RequestHandler;
import aim4.im.v2i.V2IManager;
import aim4.im.v2i.V2IManagerCallback;
import aim4.im.v2i.policy.utils.ProposalFilterResult;
import aim4.im.v2i.policy.utils.ReservationRecord;
import aim4.im.v2i.policy.utils.ReserveParam;
import aim4.im.v2i.reservation.AczManager;
import aim4.im.v2i.reservation.ReservationGrid;
import aim4.im.v2i.reservation.ReservationGridManager;
import aim4.map.Road;
import aim4.msg.i2v.Confirm;
import aim4.msg.i2v.Reject;
import aim4.msg.v2i.*;
import aim4.sim.AutoDriverOnlySimulator;
import aim4.sim.StatCollector;
import aim4.util.HashMapRegistry;
import aim4.util.Registry;
import aim4.vehicle.VehicleUtil;
import aim4.vehicle.VinRegistry;

import java.util.*;

/**
 * The base policy.
 */
public final class BasePolicy implements Policy, BasePolicyCallback {

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


  public static HashMap<Integer, Request.Proposal> CurrentState = new HashMap<Integer, Request.Proposal>();

  public static HashMap<Double, Integer> LaneCount = new HashMap<Double, Integer>();


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
          List<Request.Proposal> proposals,
          double currentTime) {
    // copy the proposals to a list first.
    List<Request.Proposal> myProposals =
            new LinkedList<Request.Proposal>(proposals);




    // Remove proposals whose arrival time is smaller than or equal to the
    // the current time.
    BasePolicy.removeProposalWithLateArrivalTime(myProposals, currentTime);
    if (myProposals.isEmpty()) {
      return new ProposalFilterResult(Reject.Reason.ARRIVAL_TIME_TOO_LATE);
    }
    // Check to see if not all of the arrival times in this reservation
    // request are too far in the future
    BasePolicy.removeProposalWithLargeArrivalTime(
            myProposals, currentTime + V2IManager.MAXIMUM_FUTURE_RESERVATION_TIME);
    if (myProposals.isEmpty()) {
      return new ProposalFilterResult(Reject.Reason.ARRIVAL_TIME_TOO_LARGE);
    }

   /* //Added for proof of concept, removes lanes which do not have a high enough priority,
    // based on real-time traffic patterns and congestion
    BasePolicy.removeProposalForNonPriorityLanes(
            myProposals);
    if (myProposals.isEmpty()) {
      return new ProposalFilterResult(Reject.Reason.NO_CLEAR_PATH);
    }*/

    System.out.println("New proposals: ");
    ProposalFilterResult current = new ProposalFilterResult(myProposals);
    System.out.println(current.getProposals().get(0).getArrivalLaneID() + " to " + current.getProposals().get(0).getDepartureLaneID());
    System.out.println();







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
          List<Request.Proposal> proposals,
          double currentTime) {
    for (Iterator<Request.Proposal> tpIter = proposals.listIterator();
         tpIter.hasNext(); ) {
      Request.Proposal prop = tpIter.next();
      // If this one is in the past
      if (prop.getArrivalTime() <= currentTime) {
        tpIter.remove();
      }
    }
  }

  /**
   * Remove proposals going to non-priority lanes
   *
   * @param proposals   a list of proposals
   */
  private static void removeProposalForNonPriorityLanes(
          List<Request.Proposal> proposals) {
    for (Iterator<Request.Proposal> tpIter = proposals.listIterator();
         tpIter.hasNext(); ) {
      Request.Proposal prop = tpIter.next();

      System.out.println("Arrival Lane Weighted Priorities: ");

      AutoDriverOnlySimulator.ArrivalLaneIDsWeightedPriorities.entrySet().forEach(entry -> {
        System.out.println(entry.getKey() + " " + entry.getValue());
      });

      System.out.println("Departure Lane Weighted Priorities: ");

      AutoDriverOnlySimulator.DepartureLaneIDsWeightedPriorities.entrySet().forEach(entry -> {
        System.out.println(entry.getKey() + " " + entry.getValue());
      });
      System.out.println();

      System.out.println("Congestion Lane Weighted Priorities: ");

      AutoDriverOnlySimulator.CongestionWeightedPriorities.entrySet().forEach(entry -> {
        System.out.println(entry.getKey() + " " + entry.getValue());
      });
      System.out.println();


      double maxArrivalPriority = maxUsingIteration(AutoDriverOnlySimulator.ArrivalLaneIDsWeightedPriorities);
      double maxDeparturePriority = maxUsingIteration(AutoDriverOnlySimulator.DepartureLaneIDsWeightedPriorities);
      double maxCongestionPriority = maxUsingIteration(AutoDriverOnlySimulator.CongestionWeightedPriorities);


      if ((maxArrivalPriority - (AutoDriverOnlySimulator.ArrivalLaneIDsWeightedPriorities.get(prop.getArrivalLaneID())) > 0.2) && (maxDeparturePriority - (AutoDriverOnlySimulator.DepartureLaneIDsWeightedPriorities.get(prop.getDepartureLaneID())) > 0.2)) {
        System.out.println("Rejected");
        tpIter.remove();
      }

      int total_vehicles = AutoDriverOnlySimulator.VehicleCountTotal;

      if(total_vehicles > 30) {
        if (((maxCongestionPriority - (AutoDriverOnlySimulator.CongestionWeightedPriorities.get(prop.getArrivalLaneID()))) > 0.2) && ((maxCongestionPriority - (AutoDriverOnlySimulator.CongestionWeightedPriorities.get(prop.getDepartureLaneID()))) < 0.2)) {
          System.out.println("Rejected");
          tpIter.remove();
        }
      }


    }
  }

  public static <K, V extends Comparable<V>> V maxUsingIteration(Map<K, V> map) {
    Map.Entry<K, V> maxEntry = null;
    for (Map.Entry<K, V> entry : map.entrySet()) {
      if (maxEntry == null || entry.getValue()
              .compareTo(maxEntry.getValue()) > 0) {
        maxEntry = entry;
      }
    }
    return maxEntry.getValue();
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
          List<Request.Proposal> proposals,
          double futureTime) {
    for (Iterator<Request.Proposal> tpIter = proposals.listIterator();
         tpIter.hasNext(); ) {
      Request.Proposal prop = tpIter.next();
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
  private RequestHandler requestHandler;

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
  private StatCollector<BasePolicy> statCollector;


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
  public BasePolicy(V2IManagerCallback im, RequestHandler requestHandler) {
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
  public BasePolicy(V2IManagerCallback im,
                    RequestHandler requestHandler,
                    StatCollector<BasePolicy> statCollector) {
    this.im = im;
    this.statCollector = statCollector;
    setRequestHandler(requestHandler);
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
  public void setRequestHandler(RequestHandler RequestHandler) {
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
    Integer gridTicket =
            im.getReservationGridManager().accept(reserveParam.getGridPlan());
    Integer aczTicket =
            reserveParam.getAczManager().accept(reserveParam.getAczPlan());
    assert gridTicket == vin;
    assert aczTicket == vin;

    // send the confirm message
    int reservationId = reservationRecordRegistry.getNewId();
    Confirm confirmMsg =
            new Confirm(im.getId(),
                    vin,
                    reservationId,
                    latestRequestId,
                    reserveParam.getSuccessfulProposal().getArrivalTime(),
                    EARLY_ERROR, LATE_ERROR,
                    reserveParam.getSuccessfulProposal().getArrivalVelocity(),
                    reserveParam.getSuccessfulProposal().getArrivalLaneID(),
                    reserveParam.getSuccessfulProposal().getDepartureLaneID(),
                    im.getACZ(reserveParam.getSuccessfulProposal()
                            .getDepartureLaneID()).getMaxSize(),
                    reserveParam.getGridPlan().getAccelerationProfile());
    im.sendI2VMessage(confirmMsg);

    // bookkeeping
    ReservationRecord r =
            new ReservationRecord(
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
  public void sendRejectMsg(int vin, int latestRequestId, Reject.Reason reason) {
    im.sendI2VMessage(new Reject(im.getId(),
            vin,
            latestRequestId,
            im.getCurrentTime(), // can re-send request
            // immediately
            reason));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ReserveParam findReserveParam(Request msg,
                                       List<Request.Proposal> proposals) {
    int vin = msg.getVin();

    CurrentState.put(vin, proposals.get(0));



    // Okay, now let's actually try some of these proposals
    Request.Proposal successfulProposal = null;
    ReservationGridManager.Plan gridPlan = null;
    AczManager aczManager = null;
    AczManager.Plan aczPlan = null;

    for (Request.Proposal proposal : proposals) {
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
      requestHandler.processRequestMsg((Request) msg);
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
   * After process V2I messages
   */
  public void processV2IMessageDone(){



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
      System.err.printf("BasePolicy::processCancelMsg(): " +
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
        System.err.printf("BasePolicy::processCancelMsg(): " +
                "The VIN of the message is different from the VIN " +
                "on the record.\n");
      }
      // do nothing with the done message since the reservation grid is
      // automatically cleaned.
    } else {
      System.err.printf("BasePolicy::processDoneMsg(): " +
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
  public StatCollector<BasePolicy> getStatCollector() {
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
