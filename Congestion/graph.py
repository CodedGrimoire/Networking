import matplotlib.pyplot as plt
import pandas as pd
import numpy as np

def simulate_tcp_reno(num_rounds=100, packet_loss_rounds=None):
    """
    Simulate TCP Reno congestion control algorithm
    
    Args:
        num_rounds: Number of transmission rounds to simulate
        packet_loss_rounds: List of rounds where packet loss occurs
    """
    if packet_loss_rounds is None:
        # Default packet loss events - simulate realistic loss pattern
        packet_loss_rounds = [25, 55, 85]
    
    rounds = []
    cwnd_values = []
    phases = []  # Track whether in slow start or congestion avoidance
    
    cwnd = 1.0  # Start with congestion window of 1
    ssthresh = 16.0  # Initial slow start threshold
    
    for round_num in range(1, num_rounds + 1):
        rounds.append(round_num)
        cwnd_values.append(cwnd)
        
        # Check if packet loss occurs this round
        if round_num in packet_loss_rounds:
            # Packet loss detected - enter fast recovery
            ssthresh = max(cwnd / 2, 2)  # Set ssthresh to half of current cwnd
            cwnd = ssthresh  # Set cwnd to ssthresh (fast recovery)
            phases.append('Fast Recovery')
        else:
            # No packet loss - normal growth
            if cwnd < ssthresh:
                # Slow Start Phase - exponential growth
                cwnd = cwnd * 2  # Double the congestion window
                phases.append('Slow Start')
            else:
                # Congestion Avoidance Phase - linear growth
                cwnd = cwnd + 1  # Increase by 1 per RTT
                phases.append('Congestion Avoidance')
    
    return rounds, cwnd_values, phases

# Generate TCP Reno simulation data
rounds, cwnd_values, phases = simulate_tcp_reno(num_rounds=100)

# Create DataFrame
data = pd.DataFrame({
    'Round': rounds,
    'CWND': cwnd_values,
    'Phase': phases
})

# Save to CSV for future use
data.to_csv('tcp_reno_simulation.csv', index=False)

# Create professional-looking plot
plt.style.use('default')
fig, ax = plt.subplots(figsize=(12, 7), dpi=100)

# Professional color palette
line_color = '#2E5984'
marker_color = '#4A90A4'
background_color = '#FAFAFA'

# Set background
fig.patch.set_facecolor('white')
ax.set_facecolor(background_color)

# Plot the data
ax.plot(data['Round'], data['CWND'], 
        marker='o', 
        markersize=3,
        markerfacecolor=marker_color,
        markeredgecolor=line_color,
        markeredgewidth=0.8,
        linewidth=2,
        color=line_color,
        alpha=0.9)

# Highlight slow start phases with different color
slow_start_data = data[data['Phase'] == 'Slow Start']
if not slow_start_data.empty:
    ax.plot(slow_start_data['Round'], slow_start_data['CWND'], 
            marker='o', 
            markersize=3,
            markerfacecolor='#E74C3C',
            markeredgecolor='#C0392B',
            markeredgewidth=0.8,
            linewidth=2,
            color='#E74C3C',
            alpha=0.8,
            label='Slow Start')

# Add legend
if not slow_start_data.empty:
    # Create custom legend
    from matplotlib.lines import Line2D
    legend_elements = [
        Line2D([0], [0], color='#E74C3C', marker='o', markersize=6, 
               label='Slow Start (Exponential Growth)', linewidth=2),
        Line2D([0], [0], color=line_color, marker='o', markersize=6, 
               label='Congestion Avoidance (Linear Growth)', linewidth=2)
    ]
    ax.legend(handles=legend_elements, loc='upper right', 
              frameon=True, fancybox=True, shadow=True, fontsize=10)

# Customize title and labels
ax.set_title('TCP Reno Congestion Window Growth\n(Slow Start → Congestion Avoidance)', 
             fontsize=16, fontweight='600', color='#2C3E50', pad=20)

ax.set_xlabel('Transmission Round', fontsize=12, fontweight='500', color='#34495E')
ax.set_ylabel('Congestion Window (CWND)', fontsize=12, fontweight='500', color='#34495E')

# Clean grid
ax.grid(True, linestyle='-', alpha=0.2, color='#BDC3C7', linewidth=0.5)
ax.set_axisbelow(True)

# Clean spines
for spine in ax.spines.values():
    spine.set_color('#BDC3C7')
    spine.set_linewidth(1)

ax.spines['top'].set_visible(False)
ax.spines['right'].set_visible(False)

# Customize ticks
ax.tick_params(axis='both', which='major', labelsize=10, colors='#5D6D7E', length=4, width=1)

# Set reasonable axis limits
ax.set_xlim(0, max(rounds) + 2)
ax.set_ylim(0, max(cwnd_values) + 2)

# Add annotations for key events
packet_loss_rounds = [25, 55, 85]
for loss_round in packet_loss_rounds:
    if loss_round <= max(rounds):
        loss_cwnd = data[data['Round'] == loss_round]['CWND'].iloc[0]
        ax.annotate('Packet Loss', 
                   xy=(loss_round, loss_cwnd), 
                   xytext=(loss_round + 5, loss_cwnd + 5),
                   arrowprops=dict(arrowstyle='->', color='#E67E22', lw=1.5),
                   fontsize=9, color='#E67E22', fontweight='500')

plt.tight_layout(pad=2.0)

# Save the plot
plt.savefig('tcp_reno_correct_behavior.png', dpi=300, bbox_inches='tight',
            facecolor='white', edgecolor='none')

plt.show()

# Print some statistics
print("TCP Reno Simulation Statistics:")
print(f"Total rounds: {len(rounds)}")
print(f"Maximum CWND reached: {max(cwnd_values):.1f}")
print(f"Number of slow start phases: {len(data[data['Phase'] == 'Slow Start'])}")
print(f"Number of congestion avoidance phases: {len(data[data['Phase'] == 'Congestion Avoidance'])}")
print(f"Packet loss events at rounds: {packet_loss_rounds}")