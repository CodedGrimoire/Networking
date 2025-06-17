import matplotlib.pyplot as plt
import pandas as pd
import os

def plot_tcp_reno_from_data():
    """
    Plot TCP Reno CWND behavior using actual data from the Java client
    """
    
    # Check if CWND data file exists
    if not os.path.exists('tcp_reno_simulation.csv'):
        print("tcp_reno_simulation.csv not found. Run the Java client first.")
        return
    
    # Load the actual CWND data
    try:
        cwnd_data = pd.read_csv('tcp_reno_simulation.csv')
        print(f"Loaded {len(cwnd_data)} data points")
        print(f"Columns: {list(cwnd_data.columns)}")
    except Exception as e:
        print(f"Error loading CWND data: {e}")
        return
    
    # Identify phases based on CWND behavior
    cwnd_data['Phase'] = 'Congestion Avoidance'
    
    for i in range(len(cwnd_data)):
        current_cwnd = cwnd_data.iloc[i]['CWND']
        
        if i == 0:
            cwnd_data.iloc[i, cwnd_data.columns.get_loc('Phase')] = 'Slow Start'
        else:
            prev_cwnd = cwnd_data.iloc[i-1]['CWND']
            
            # Detect significant drops (timeouts or fast recovery)
            if current_cwnd < prev_cwnd * 0.7:
                cwnd_data.iloc[i, cwnd_data.columns.get_loc('Phase')] = 'Fast Recovery'
            # Detect exponential growth (slow start)
            elif current_cwnd >= prev_cwnd * 1.5:
                cwnd_data.iloc[i, cwnd_data.columns.get_loc('Phase')] = 'Slow Start'
    
    # Create the plot
    plt.figure(figsize=(12, 7))
    
    # Color scheme for different phases
    colors = {
        'Slow Start': '#E74C3C',
        'Congestion Avoidance': '#3498DB', 
        'Fast Recovery': '#F39C12'
    }
    
    # Plot main line
    plt.plot(cwnd_data['Round'], cwnd_data['CWND'],
             linewidth=2, color='#2C3E50', alpha=0.8)
    
    # Color-code different phases
    for phase, color in colors.items():
        phase_data = cwnd_data[cwnd_data['Phase'] == phase]
        if not phase_data.empty:
            plt.scatter(phase_data['Round'], phase_data['CWND'],
                       c=color, label=phase, s=40, alpha=0.9, zorder=3)
    
    plt.title('TCP Reno Congestion Window Evolution', fontsize=16, fontweight='bold')
    plt.xlabel('Transmission Round', fontsize=12)
    plt.ylabel('Congestion Window (CWND)', fontsize=12)
    plt.legend(loc='upper left')
    plt.grid(True, alpha=0.3)
    
    # Set limits
    plt.xlim(0, max(cwnd_data['Round']) + 1)
    plt.ylim(0, max(cwnd_data['CWND']) + 2)
    
    plt.tight_layout()
    plt.savefig('tcp_reno_behavior.png', dpi=300, bbox_inches='tight')
    plt.show()
    
    # Print phase statistics
    print("\nPhase distribution:")
    for phase in colors.keys():
        count = len(cwnd_data[cwnd_data['Phase'] == phase])
        print(f"{phase}: {count} rounds ({count/len(cwnd_data)*100:.1f}%)")

if __name__ == "__main__":
    plot_tcp_reno_from_data()