import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { API_BASE_URL } from '@/config/api';

type LeaderboardEntry = {
  playerId: string;
  name: string;
  score: number;
  completedChallengeCount: number;
  skippedChallengeCount: number;
  totalChallenges: number;
};

export default function ResultsScreen() {
  const params = useLocalSearchParams<{
    code?: string | string[];
  }>();
  const code = Array.isArray(params.code) ? params.code[0] : params.code;
  const [leaderboard, setLeaderboard] = useState<LeaderboardEntry[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (!code) {
      setIsLoading(false);
      return;
    }

    let mounted = true;
    const loadLeaderboard = async () => {
      try {
        const response = await fetch(
          `${API_BASE_URL}/games/${encodeURIComponent(code)}/leaderboard`
        );
        if (!response.ok) {
          return;
        }
        const latestLeaderboard: LeaderboardEntry[] = await response.json();
        if (mounted) {
          setLeaderboard(latestLeaderboard);
        }
      } catch {
        // The final screen remains read-only if the leaderboard request fails.
      } finally {
        if (mounted) {
          setIsLoading(false);
        }
      }
    };

    void loadLeaderboard();
    return () => {
      mounted = false;
    };
  }, [code]);

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.eyebrow}>FINAL RESULTS</Text>
      <Text style={styles.title}>Game complete</Text>
      <Text style={styles.subtitle}>All challenge decisions have been resolved.</Text>
      {isLoading ? <Text style={styles.subtitle}>Loading final scores...</Text> : null}
      {!isLoading && leaderboard.length === 0 ? (
        <Text style={styles.subtitle}>Final scores are unavailable.</Text>
      ) : null}
      {leaderboard.map((entry, index) => (
        <View key={entry.playerId} style={styles.card}>
          <View style={styles.topRow}>
            <Text style={styles.rank}>#{index + 1}</Text>
            <Text style={styles.name}>{entry.name}</Text>
            <Text style={styles.score}>{entry.score} pts</Text>
          </View>
          <Text style={styles.counts}>
            {entry.completedChallengeCount} completed · {entry.skippedChallengeCount} skipped ·{' '}
            {entry.totalChallenges} total
          </Text>
        </View>
      ))}
      <Pressable style={styles.homeButton} onPress={() => router.replace('/')}>
        <Text style={styles.homeButtonText}>Home</Text>
      </Pressable>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    backgroundColor: '#F7F4EE',
    padding: 24,
    paddingTop: 72,
  },
  eyebrow: {
    color: '#B14B4B',
    fontSize: 13,
    fontWeight: '800',
    letterSpacing: 2,
  },
  title: {
    color: '#20212A',
    fontSize: 34,
    fontWeight: '800',
    marginTop: 8,
  },
  subtitle: {
    color: '#666875',
    fontSize: 16,
    lineHeight: 23,
    marginTop: 8,
  },
  card: {
    backgroundColor: '#FFFFFF',
    borderColor: '#E1E4EE',
    borderRadius: 16,
    borderWidth: 1,
    marginTop: 16,
    padding: 16,
  },
  topRow: {
    alignItems: 'center',
    flexDirection: 'row',
  },
  rank: {
    color: '#B14B4B',
    fontSize: 20,
    fontWeight: '800',
    width: 52,
  },
  name: {
    color: '#20212A',
    flex: 1,
    fontSize: 18,
    fontWeight: '700',
  },
  score: {
    color: '#20212A',
    fontSize: 18,
    fontWeight: '800',
  },
  counts: {
    color: '#666875',
    fontSize: 14,
    marginTop: 10,
  },
  homeButton: {
    alignItems: 'center',
    backgroundColor: '#20212A',
    borderRadius: 12,
    marginTop: 28,
    padding: 15,
  },
  homeButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '700',
  },
});
