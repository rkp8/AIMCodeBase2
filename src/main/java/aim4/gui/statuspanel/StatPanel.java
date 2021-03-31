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
package aim4.gui.statuspanel;

import aim4.config.Constants;
import aim4.gui.StatusPanelInterface;
import aim4.gui.Viewer;
import aim4.gui.component.FormattedLabel;
import aim4.sim.Simulator;

import javax.swing.*;
import java.awt.*;

/**
 * The statistics Panel
 */
public class StatPanel extends JPanel
                       implements StatusPanelInterface {

  private static final long serialVersionUID = 1L;

  // ///////////////////////////////
  // PRIVATE FIELDS
  // ///////////////////////////////

  /** The current time in the simulator. */
  private FormattedLabel currentTimeLabel =
    new FormattedLabel("Current Time: ", "%8.2f s", 10);
  /** The number of completed vehicles. */
  private FormattedLabel overallCompletedVehiclesLabel =
    new FormattedLabel("        Completed Vehicles: ", "%5d", 5);
  /** The number of vehicles passing through the system per second. */
  private FormattedLabel overallThroughput =
          new FormattedLabel("       Gross Throughput: ", "%8.2f vehicles/second", 5);
  /** The average time delay experienced by a vehicle */
  private FormattedLabel avgTimeDelay =
          new FormattedLabel("         Average Time Delay ", "%8.2f s", 5);
  /** The average amount of data transmitted. */
  private FormattedLabel overallAverageTransmittedLabel =
    new FormattedLabel("         Average Data Transmitted: ", "%5.2f kB", 8);
  /** The average amount of data received. */
  private FormattedLabel overallAverageReceivedLabel =
    new FormattedLabel("         Average Data Received: ", "%5.2f kB", 8);

  /** The maximum time delay */
  private FormattedLabel maxDelayLabel =
          new FormattedLabel("         Maximum Time Delay: ", "%8.2f s", 8);
  /** The average amount of data received. */
  private FormattedLabel StdDevLabel =
          new FormattedLabel("         Delay σ: ", "%8.2f s", 8);



  /** The viewer object */
  private Viewer viewer;

  // ///////////////////////////////
  // CONSTRUCTORS
  // ///////////////////////////////

  /**
   * The statistics panel.
   *
   * @param viewer  the viewer object
   */
  public StatPanel(Viewer viewer) {
    this.viewer = viewer;

    GridBagLayout gridbag = new GridBagLayout();
    setLayout(gridbag);

    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.BOTH;
    c.weightx = 1.0;
    c.weighty = 1.0;

    // Time
    c.gridwidth = 1; // restore default
    gridbag.setConstraints(currentTimeLabel, c);
    add(currentTimeLabel);
    // Completed Vehicles
    c.gridwidth = 1;
    gridbag.setConstraints(overallCompletedVehiclesLabel, c);
    add(overallCompletedVehiclesLabel);
    //Throughput
    c.gridwidth = 1;
    gridbag.setConstraints(overallThroughput, c);
    add(overallThroughput);
    // Time Delay
    c.gridwidth = 1;
    gridbag.setConstraints(avgTimeDelay, c);
    add(avgTimeDelay);

    // Information Transmitted
    c.gridwidth = 1; // restore default
    gridbag.setConstraints(overallAverageTransmittedLabel, c);
    add(overallAverageTransmittedLabel);
    // Information Received
    c.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(overallAverageReceivedLabel, c);
    add(overallAverageReceivedLabel);

    // Max Delay
    c.gridwidth = 1; // restore default
    gridbag.setConstraints(maxDelayLabel, c);
    add(maxDelayLabel);
    // Delay Standard Deviation
    c.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(StdDevLabel, c);
    add(StdDevLabel);
  }

  // ///////////////////////////////
  // PUBLIC METHODS
  // ///////////////////////////////

  /**
   * {@inheritDoc}
   */
  @Override
  public void update() {
    Simulator sim = viewer.getSimulator();
    if (sim != null) {
      // Current Time
      currentTimeLabel.update(sim.getSimulationTime());
      // Completed Vehicles
      overallCompletedVehiclesLabel.update(sim.getNumCompletedVehicles());

      //Throughput
      overallThroughput.update(sim.getNumCompletedVehicles()/sim.getSimulationTime());

      // Time Delay

      avgTimeDelay.update(sim.getTimeDelay());


      // Average Data Transmitted
      overallAverageTransmittedLabel.update(sim
        .getAvgBitsTransmittedByCompletedVehicles()
        / Constants.BITS_PER_KB);
      // Average Data Received
      overallAverageReceivedLabel.update(sim
        .getAvgBitsReceivedByCompletedVehicles()
        / Constants.BITS_PER_KB);


      // Max Time Delay

      maxDelayLabel.update(sim.getMaxTimeDelay());


      // Time Delay std dev

      StdDevLabel.update(sim.getStdDeviation());

    } else {
      clear();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void clear() {
    currentTimeLabel.clear();
    overallCompletedVehiclesLabel.clear();
    overallAverageTransmittedLabel.clear();
    overallAverageReceivedLabel.clear();
    overallThroughput.clear();
    avgTimeDelay.clear();
    maxDelayLabel.clear();
    StdDevLabel.clear();
  }
}