import matplotlib.pyplot as plt
import pandas as pd
import os

def plot_tcp_comparison():
    """
    Plot both TCP Tahoe and Reno on the same graph with different colors
    """
    
    # Load data files
    tahoe_data = None
    reno_data = None
    
    # Try to load Tahoe data
    tahoe_files = ['tcp_tahoe_data.csv', 'tcp_tahoe_simulation.csv']
    for filename in tahoe_files:
        if os.path.exists(filename):
            try:
                tahoe_data = pd.read_csv(filename)
                print(f"✅ Loaded TCP Tahoe: {filename}")
                break
            except Exception as e:
                print(f"❌ Error loading {filename}: {e}")
    
    # Try to load Reno data
    if os.path.exists('tcp_reno_simulation.csv'):
        try:
            reno_data = pd.read_csv('tcp_reno_simulation.csv')
            print(f"✅ Loaded TCP Reno: tcp_reno_simulation.csv")
        except Exception as e:
            print(f"❌ Error loading Reno data: {e}")
    
    # Check if we have data to plot
    if tahoe_data is None and reno_data is None:
        print("❌ No TCP simulation data found!")
        print("Please run the Java simulations first.")
        return
    
    # Create the plot
    plt.figure(figsize=(14, 8))
    
    # Plot TCP Tahoe
    if tahoe_data is not None:
        plt.plot(tahoe_data['Round'], tahoe_data['CWND'], 
                linewidth=3, color='#E74C3C', alpha=0.8, 
                label='TCP Tahoe CWND', marker='o', markersize=5, markevery=2)
        
        plt.plot(tahoe_data['Round'], tahoe_data['SSThresh'], 
                linewidth=2, color='#C0392B', alpha=0.6, 
                linestyle='--', label='Tahoe SSThresh')
        
        # Mark packet loss events
        if 'Event' in tahoe_data.columns:
            loss_events = tahoe_data[tahoe_data['Event'].str.contains('Loss|Dup ACK', na=False)]
            if not loss_events.empty:
                plt.scatter(loss_events['Round'], loss_events['CWND'],
                           color='#E74C3C', s=100, marker='x', linewidths=3,
                           label='Tahoe Loss Events', zorder=5)
    
    # Plot TCP Reno
    if reno_data is not None:
        plt.plot(reno_data['Round'], reno_data['CWND'], 
                linewidth=3, color='#3498DB', alpha=0.8, 
                label='TCP Reno CWND', marker='s', markersize=5, markevery=2)
        
        plt.plot(reno_data['Round'], reno_data['SSThresh'], 
                linewidth=2, color='#2980B9', alpha=0.6, 
                linestyle='--', label='Reno SSThresh')
        
        # Mark Fast Recovery periods
        if 'Phase' in reno_data.columns:
            fast_recovery = reno_data[reno_data['Phase'] == 'Fast Recovery']
            if not fast_recovery.empty:
                plt.scatter(fast_recovery['Round'], fast_recovery['CWND'],
                           color='#F39C12', s=80, marker='D', 
                           label='Fast Recovery', zorder=4)
        
        # Mark timeout events
        if 'Event' in reno_data.columns:
            timeout_events = reno_data[reno_data['Event'].str.contains('Timeout', na=False)]
            if not timeout_events.empty:
                plt.scatter(timeout_events['Round'], timeout_events['CWND'],
                           color='#3498DB', s=100, marker='x', linewidths=3,
                           label='Reno Timeout Events', zorder=5)
    
    # Formatting
    plt.title('TCP Tahoe vs Reno: Congestion Window Comparison', 
              fontsize=18, fontweight='bold', pad=20)
    plt.xlabel('Transmission Round', fontsize=14)
    plt.ylabel('Congestion Window (CWND)', fontsize=14)
    plt.legend(loc='upper left', fontsize=12, framealpha=0.9)
    plt.grid(True, alpha=0.3)
    
    # Set limits
    max_rounds = 0
    max_cwnd = 0
    if tahoe_data is not None:
        max_rounds = max(max_rounds, max(tahoe_data['Round']))
        max_cwnd = max(max_cwnd, max(tahoe_data['CWND']))
    if reno_data is not None:
        max_rounds = max(max_rounds, max(reno_data['Round']))
        max_cwnd = max(max_cwnd, max(reno_data['CWND']))
    
    plt.xlim(0, max_rounds + 2)
    plt.ylim(0, max_cwnd + 3)
    
    # Add protocol info box
    info_text = []
    if tahoe_data is not None:
        tahoe_max = tahoe_data['CWND'].max()
        tahoe_avg = tahoe_data['CWND'].mean()
        info_text.append(f"Tahoe - Max: {tahoe_max}, Avg: {tahoe_avg:.1f}")
    
    if reno_data is not None:
        reno_max = reno_data['CWND'].max()
        reno_avg = reno_data['CWND'].mean()
        info_text.append(f"Reno - Max: {reno_max}, Avg: {reno_avg:.1f}")
    
    if info_text:
        plt.text(0.02, 0.98, '\n'.join(info_text), 
                transform=plt.gca().transAxes, fontsize=10,
                verticalalignment='top', bbox=dict(boxstyle='round', 
                facecolor='white', alpha=0.8))
    
    plt.tight_layout()
    plt.savefig('tcp_comparison.png', dpi=300, bbox_inches='tight')
    plt.show()
    
    # Print comparison summary
    print("\n" + "="*50)
    print("📊 TCP COMPARISON SUMMARY")
    print("="*50)
    
    if tahoe_data is not None:
        print(f"🔴 TCP Tahoe:")
       
        if 'Event' in tahoe_data.columns:
            losses = len(tahoe_data[tahoe_data['Event'].str.contains('Loss|Dup ACK', na=False)])
            print(f"   Loss Events: {losses}")
    
    if reno_data is not None:
        print(f"🔵 TCP Reno:")
        
        if 'Event' in reno_data.columns:
            timeouts = len(reno_data[reno_data['Event'].str.contains('Timeout', na=False)])
            print(f"   Timeout Events: {timeouts}")
        if 'Phase' in reno_data.columns:
            fast_recovery_rounds = len(reno_data[reno_data['Phase'] == 'Fast Recovery'])
            print(f"   Fast Recovery Rounds: {fast_recovery_rounds}")
    
    print("\n🔑 Key Difference:")
    print("   • Tahoe: Resets CWND to 1 on ANY loss")
    print("   • Reno: Uses Fast Recovery for triple dup ACKs")
    print(f"\n📁 Generated: tcp_comparison.png")

if __name__ == "__main__":
    print("🚀 TCP Tahoe vs Reno Comparison")
    print("="*40)
    plot_tcp_comparison()