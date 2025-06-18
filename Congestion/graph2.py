import matplotlib.pyplot as plt
import pandas as pd
import os

def plot_tcp_tahoe_from_data():
    """
    Plot TCP Tahoe CWND behavior using actual data from the Java simulation
    """
    
    # Check if CWND data file exists
    if not os.path.exists('tcp_tahoe_simulation.csv'):
        print("tcp_tahoe_simulation.csv not found. Run the Java TCP Tahoe simulation first.")
        return
    
    # Load the actual CWND data
    try:
        cwnd_data = pd.read_csv('tcp_tahoe_simulation.csv')
        print(f"Loaded {len(cwnd_data)} data points")
        print(f"Columns: {list(cwnd_data.columns)}")
    except Exception as e:
        print(f"Error loading CWND data: {e}")
        return
    
    # Create the plot
    plt.figure(figsize=(14, 8))
    
    # Color scheme for different phases
    colors = {
        'Slow Start': '#E74C3C',          # Red
        'Congestion Avoidance': '#3498DB', # Blue
        'Packet Loss': '#F39C12'          # Orange for loss events
    }
    
    # Plot main line
    plt.plot(cwnd_data['Round'], cwnd_data['CWND'],
             linewidth=2.5, color='#2C3E50', alpha=0.8, label='CWND')
    
    # Plot SSThresh line
    plt.plot(cwnd_data['Round'], cwnd_data['SSThresh'],
             linewidth=2, color='#9B59B6', alpha=0.7, linestyle='--', label='SSThresh')
    
    # Color-code different phases
    for phase, color in colors.items():
        if phase == 'Packet Loss':
            # Highlight packet loss events
            loss_data = cwnd_data[cwnd_data['Event'] == 'Packet Loss']
            if not loss_data.empty:
                plt.scatter(loss_data['Round'], loss_data['CWND'],
                           c=color, label='Packet Loss', s=80, alpha=0.9, zorder=4,
                           marker='x', linewidths=3)
        else:
            # Color phases
            phase_data = cwnd_data[cwnd_data['Phase'] == phase]
            if not phase_data.empty:
                plt.scatter(phase_data['Round'], phase_data['CWND'],
                           c=color, label=phase, s=30, alpha=0.8, zorder=3)
    
    # Formatting
    plt.title('TCP Tahoe Congestion Window Evolution', fontsize=18, fontweight='bold', pad=20)
    plt.xlabel('Transmission Round', fontsize=14)
    plt.ylabel('Congestion Window (CWND)', fontsize=14)
    plt.legend(loc='upper left', fontsize=12)
    plt.grid(True, alpha=0.3)
    
    # Set limits with some padding
    plt.xlim(0, max(cwnd_data['Round']) + 2)
    plt.ylim(0, max(cwnd_data['CWND']) + 3)
    
    # Add annotations for key characteristics
   
    
    plt.tight_layout()
    plt.savefig('tcp_tahoe_behavior.png', dpi=300, bbox_inches='tight')
    plt.show()
    
    # Print phase statistics
    print("\nTCP Tahoe Simulation Analysis:")
    print("=" * 40)
    
    # Phase distribution
    phase_counts = cwnd_data['Phase'].value_counts()
    print(f"\nPhase Distribution:")
    for phase in ['Slow Start', 'Congestion Avoidance']:
        if phase in phase_counts:
            count = phase_counts[phase]
            percentage = (count / len(cwnd_data)) * 100
            print(f"  {phase}: {count} rounds ({percentage:.1f}%)")
    
    # Event statistics
    event_counts = cwnd_data['Event'].value_counts()
    print(f"\nEvent Statistics:")
    for event in event_counts.index:
        count = event_counts[event]
        percentage = (count / len(cwnd_data)) * 100
        print(f"  {event}: {count} rounds ({percentage:.1f}%)")
    
    # Performance metrics
    max_cwnd = cwnd_data['CWND'].max()
    avg_cwnd = cwnd_data['CWND'].mean()
    packet_losses = len(cwnd_data[cwnd_data['Event'] == 'Packet Loss'])
    
    print(f"\nPerformance Metrics:")
    print(f"  Maximum CWND: {max_cwnd:.1f}")
    print(f"  Average CWND: {avg_cwnd:.2f}")
    print(f"  Total Packet Losses: {packet_losses}")
    print(f"  Loss Rate: {(packet_losses/len(cwnd_data)*100):.2f}%")
    
    # Show recovery behavior
    print(f"\nTCP Tahoe Recovery Behavior:")
    loss_rounds = cwnd_data[cwnd_data['Event'] == 'Packet Loss']['Round'].tolist()
    for loss_round in loss_rounds:
        loss_idx = cwnd_data[cwnd_data['Round'] == loss_round].index[0]
        if loss_idx > 0:
            prev_cwnd = cwnd_data.iloc[loss_idx-1]['CWND']
            new_ssthresh = cwnd_data.iloc[loss_idx]['SSThresh']
            print(f"  Round {loss_round}: CWND {prev_cwnd:.1f} → 1.0, SSThresh → {new_ssthresh:.1f}")



if __name__ == "__main__":
    print("TCP Tahoe Visualization Tool")
    print("=" * 40)
    
    # Main plot
    plot_tcp_tahoe_from_data()
    
    print("\n" + "=" * 40)
    
    # Detailed analysis
   
    
    print("\nVisualization complete!")
    print("Generated files:")
    print("  - tcp_tahoe_behavior.png")
  