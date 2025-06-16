import pandas as pd
import matplotlib.pyplot as plt

# Load CSV file
df = pd.read_csv("rtt_data.csv")

# Extract columns
time_data = df["TimeIndex"]
sample_rtt = df["SampleRTT"]
estimated_rtt = df["EstimatedRTT"]
timeout_interval = df["TimeoutInterval"]

# Plotting
plt.figure(figsize=(10, 6))
plt.plot(time_data, sample_rtt, 'r-', label='Sample RTT')
plt.plot(time_data, estimated_rtt, 'b-', label='Estimated RTT')
plt.plot(time_data, timeout_interval, 'g--', label='Timeout Interval')
plt.xlabel('Packet Index')
plt.ylabel('RTT (milliseconds)')
plt.title('RTT Measurements During File Transfer')
plt.legend(loc='upper right')
plt.grid(True)
plt.tight_layout()
plt.savefig('rtt_plot.png')
plt.show()