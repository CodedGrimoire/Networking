import matplotlib.pyplot as plt
import pandas as pd
import os

def plot_tcp_comparison():
    """
    Plot TCP Tahoe and Reno comparison with improved visuals
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
    
    if tahoe_data is None and reno_data is None:
        print("❌ No TCP simulation data found!")
        return
    
    # Set style
    plt.style.use('seaborn-v0_8')
    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(15, 10), height_ratios=[3, 1])
    
    # Main plot
    if tahoe_data is not None:
        ax1.plot(tahoe_data['Round'], tahoe_data['CWND'], 
                linewidth=2.5, color='#e74c3c', alpha=0.9,
                label='TCP Tahoe CWND', zorder=3)
        ax1.plot(tahoe_data['Round'], tahoe_data['SSThresh'], 
                linewidth=1.5, color='#c0392b', alpha=0.7,
                linestyle=':', label='Tahoe SSThresh', zorder=2)
    
    if reno_data is not None:
        ax1.plot(reno_data['Round'], reno_data['CWND'], 
                linewidth=2.5, color='#3498db', alpha=0.9,
                label='TCP Reno CWND', zorder=3)
        ax1.plot(reno_data['Round'], reno_data['SSThresh'], 
                linewidth=1.5, color='#2980b9', alpha=0.7,
                linestyle=':', label='Reno SSThresh', zorder=2)
        
        # Highlight Fast Recovery periods
        if 'Phase' in reno_data.columns:
            fast_recovery = reno_data[reno_data['Phase'] == 'Fast Recovery']
            if not fast_recovery.empty:
                ax1.fill_between(fast_recovery['Round'], 0, fast_recovery['CWND'].max() + 2,
                               alpha=0.2, color='#f39c12', label='Fast Recovery Period')
    
    # Formatting main plot
    ax1.set_title('TCP Tahoe vs Reno: Congestion Control Comparison', 
                 fontsize=16, fontweight='bold', pad=20)
    ax1.set_ylabel('Window Size', fontsize=12, fontweight='bold')
    ax1.legend(loc='upper right', frameon=True, fancybox=True, shadow=True)
    ax1.grid(True, alpha=0.3, linestyle='-', linewidth=0.5)
    ax1.set_facecolor('#fafafa')
    
    # Phase timeline (bottom subplot)
    max_rounds = 0
    if tahoe_data is not None:
        max_rounds = max(max_rounds, max(tahoe_data['Round']))
    if reno_data is not None:
        max_rounds = max(max_rounds, max(reno_data['Round']))
    
    # Create phase indicators
    y_tahoe = 0.7
    y_reno = 0.3
    
    if tahoe_data is not None and 'Phase' in tahoe_data.columns:
        for phase in ['Slow Start', 'Congestion Avoidance']:
            phase_data = tahoe_data[tahoe_data['Phase'] == phase]
            if not phase_data.empty:
                color = '#ffcccb' if phase == 'Slow Start' else '#ffe4e1'
                for _, row in phase_data.iterrows():
                    ax2.barh(y_tahoe, 1, left=row['Round']-0.5, height=0.15, 
                            color=color, alpha=0.8, edgecolor='none')
    
    if reno_data is not None and 'Phase' in reno_data.columns:
        for phase in ['Slow Start', 'Congestion Avoidance', 'Fast Recovery']:
            phase_data = reno_data[reno_data['Phase'] == phase]
            if not phase_data.empty:
                if phase == 'Slow Start':
                    color = '#cce7ff'
                elif phase == 'Fast Recovery':
                    color = '#ffd700'
                else:
                    color = '#e6f3ff'
                
                for _, row in phase_data.iterrows():
                    ax2.barh(y_reno, 1, left=row['Round']-0.5, height=0.15, 
                            color=color, alpha=0.8, edgecolor='none')
    
    # Format phase timeline
    ax2.set_xlim(0, max_rounds + 1)
    ax2.set_ylim(0, 1)
    ax2.set_xlabel('Transmission Round', fontsize=12, fontweight='bold')
    ax2.set_ylabel('Protocol', fontsize=10)
    ax2.set_yticks([y_reno, y_tahoe])
    ax2.set_yticklabels(['Reno', 'Tahoe'])
    ax2.grid(True, alpha=0.2, axis='x')
    ax2.set_facecolor('#fafafa')
    
    # Add phase legend
    legend_elements = [
        plt.Rectangle((0,0),1,1, facecolor='#ffcccb', alpha=0.8, label='Slow Start'),
        plt.Rectangle((0,0),1,1, facecolor='#ffe4e1', alpha=0.8, label='Congestion Avoidance'),
        plt.Rectangle((0,0),1,1, facecolor='#ffd700', alpha=0.8, label='Fast Recovery')
    ]
    ax2.legend(handles=legend_elements, loc='upper right', ncol=3, frameon=True)
    
    plt.tight_layout()
    plt.savefig('tcp_comparison_improved.png', dpi=300, bbox_inches='tight', 
                facecolor='white', edgecolor='none')
    plt.show()
    
    # Summary
    print("\n" + "="*40)
    print("📊 COMPARISON SUMMARY")
    print("="*40)
    
    if tahoe_data is not None and reno_data is not None:
        tahoe_max = tahoe_data['CWND'].max()
        reno_max = reno_data['CWND'].max()
        tahoe_avg = tahoe_data['CWND'].mean()
        reno_avg = reno_data['CWND'].mean()
        
        print(f"Max CWND  - Tahoe: {tahoe_max:2d} | Reno: {reno_max:2d}")
        print(f"Avg CWND  - Tahoe: {tahoe_avg:4.1f} | Reno: {reno_avg:4.1f}")
        
        if 'Phase' in reno_data.columns:
            fast_recovery = len(reno_data[reno_data['Phase'] == 'Fast Recovery'])
            print(f"Fast Recovery Rounds: {fast_recovery}")
    
    print(f"\n📁 Saved: tcp_comparison_improved.png")

if __name__ == "__main__":
    plot_tcp_comparison()