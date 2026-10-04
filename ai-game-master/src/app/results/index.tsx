import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { Animated, Pressable, StyleSheet, Text, View } from 'react-native';
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
  const entrance = useRef(new Animated.Value(0)).current;
  const params = useLocalSearchParams<{
    code?: string | string[];
  }>();
  const code = Array.isArray(params.code) ? params.code[0] : params.code;
  const [leaderboard, setLeaderboard] = useState<LeaderboardEntry[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    Animated.timing(entrance, { toValue: 1, duration: 550, useNativeDriver: true }).start();
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
  }, [code, entrance]);

  return (
    <Animated.ScrollView contentContainerStyle={styles.container} style={{ opacity: entrance }}>
      <Text style={styles.eyebrow}>FINAL RESULTS</Text>
      <Text style={styles.title}>Game complete</Text>
      <Text style={styles.subtitle}>All challenge decisions have been resolved.</Text>
      {isLoading ? <Text style={styles.subtitle}>Loading final scores...</Text> : null}
      {!isLoading && leaderboard.length === 0 ? (
        <Text style={styles.subtitle}>Final scores are unavailable.</Text>
      ) : null}
      {leaderboard.map((entry, index) => (
        <Animated.View key={entry.playerId} style={[styles.card, { opacity: entrance }]}>
          <View style={styles.topRow}>
            <Text style={styles.rank}>#{index + 1}</Text>
            <Text style={styles.name}>{entry.name}</Text>
            <Text style={styles.score}>{entry.score} pts</Text>
          </View>
          <Text style={styles.counts}>
            {entry.completedChallengeCount} completed · {entry.skippedChallengeCount} skipped ·{' '}
            {entry.totalChallenges} total
          </Text>
        </Animated.View>
      ))}
      <Pressable style={styles.homeButton} onPress={() => router.replace('/')}>
        <Text style={styles.homeButtonText}>Home</Text>
      </Pressable>
    </Animated.ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    backgroundColor: '#10121B',
    padding: 24,
    paddingTop: 72,
  },
  eyebrow: {
    color: '#A89FFF',
    fontSize: 13,
    fontWeight: '800',
    letterSpacing: 2,
  },
  title: {
    color: '#FFFFFF',
    fontSize: 34,
    fontWeight: '800',
    marginTop: 8,
  },
  subtitle: {
    color: '#A9ADBE',
    fontSize: 16,
    lineHeight: 23,
    marginTop: 8,
  },
  card: {
    backgroundColor: '#1A1E2B',
    borderColor: '#30364B',
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
    color: '#A89FFF',
    fontSize: 20,
    fontWeight: '800',
    width: 52,
  },
  name: {
    color: '#FFFFFF',
    flex: 1,
    fontSize: 18,
    fontWeight: '700',
  },
  score: {
    color: '#50D6A4',
    fontSize: 18,
    fontWeight: '800',
  },
  counts: {
    color: '#A9ADBE',
    fontSize: 14,
    marginTop: 10,
  },
  homeButton: {
    alignItems: 'center',
    backgroundColor: '#786BFF',
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
