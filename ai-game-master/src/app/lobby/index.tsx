import { router, useLocalSearchParams } from 'expo-router';
import * as Clipboard from 'expo-clipboard';
import { API_BASE_URL } from '@/config/api';
import { useEffect, useRef, useState } from 'react';
import { Alert, View, Text, StyleSheet, Pressable } from 'react-native';

type Player = {
  id: string;
  name: string;
};

type GameStatus = 'lobby' | 'playing' | 'finished';

export default function LobbyScreen() {
const params = useLocalSearchParams<{
    code?: string | string[];
    playerId?: string | string[];
    playerName?: string | string[];
    hostPlayerId?: string | string[];
    players?: string | string[];
  }>();

  const code = Array.isArray(params.code) ? params.code[0] : params.code;
  const playerId = Array.isArray(params.playerId) ? params.playerId[0] : params.playerId;
  const playerName = Array.isArray(params.playerName) ? params.playerName[0] : params.playerName;
  const hostPlayerIdParam = Array.isArray(params.hostPlayerId)
    ? params.hostPlayerId[0]
    : params.hostPlayerId;
  const playersParam = Array.isArray(params.players)
    ? params.players[0]
    : params.players;
  const [hostPlayerId, setHostPlayerId] = useState<string | null>(
    hostPlayerIdParam ?? null
  );
  const [status, setStatus] = useState<GameStatus>('lobby');
  const [generatedChallenges, setGeneratedChallenges] = useState<
    { id: string; challenge: string; playerId?: string }[]
  >([]);
  const [challengePreview, setChallengePreview] = useState<{ id: string; challenge: string; playerId?: string }[]>([]);
  const [regenerationCount, setRegenerationCount] = useState(0);
  const [duration, setDuration] = useState<string | null>(null);
  const [selectedDuration, setSelectedDuration] = useState<string | null>(null);
  const [recommendedDuration, setRecommendedDuration] = useState<number | null>(null);
  const [preparingChallenges, setPreparingChallenges] = useState(false);
  const hasNavigatedToResults = useRef(false);
  const [players, setPlayers] = useState<Player[]>(() => {
    try {
      return playersParam ? JSON.parse(playersParam) : [];
    } catch {
      return [];
    }
  });

  useEffect(() => {
    if (!code) {
      return;
    }

    let isMounted = true;
    let hasNavigatedToGame = false;
    let intervalId: ReturnType<typeof setInterval>;
    const fetchGameState = async () => {
      try {
        const encodedCode = encodeURIComponent(code);
        const [gameResponse, playersResponse] = await Promise.all([
          fetch(`${API_BASE_URL}/games/${encodedCode}`),
          fetch(`${API_BASE_URL}/games/${encodedCode}/players`),
        ]);

        if (gameResponse.status === 404 || playersResponse.status === 404) {
          if (isMounted) {
            clearInterval(intervalId);
            Alert.alert('Game ended', 'The host ended the game.', [
              {
                text: 'OK',
                onPress: () => router.replace('/'),
              },
            ]);
          }
          return;
        }

        if (!gameResponse.ok || !playersResponse.ok) {
          return;
        }

        const game: {
          hostPlayerId?: string;
          status?: GameStatus;
          challenges?: { id: string; challenge: string; playerId?: string }[];
          regenerationCount?: number;
          duration?: string;
          recommendedDuration?: number;
        } = await gameResponse.json();
        const latestPlayers: Player[] = await playersResponse.json();
        if (isMounted) {
          if (game.hostPlayerId !== undefined) {
            setHostPlayerId(game.hostPlayerId);
          }
          const latestChallenges = Array.isArray(game.challenges) ? game.challenges : [];
          setGeneratedChallenges(latestChallenges);
          setChallengePreview(playerId
            ? latestChallenges.filter((challenge) => challenge.playerId === playerId)
            : []);
          if (game.regenerationCount !== undefined) setRegenerationCount(game.regenerationCount);
          if (game.duration !== undefined && !selectedDuration) setDuration(game.duration);
          if (game.recommendedDuration !== undefined) setRecommendedDuration(game.recommendedDuration);
          if (game.status === 'lobby' || game.status === 'playing' || game.status === 'finished') {
            setStatus(game.status);
            if (game.status === 'finished' && !hasNavigatedToResults.current && playerId) {
              hasNavigatedToResults.current = true;
              console.log('GAME STATUS FINISHED — navigating to final scoreboard');
              router.replace({
                pathname: '/results',
                params: { code, playerId },
              });
              return;
            }
            if (game.status === 'playing' && !hasNavigatedToGame && playerId) {
              hasNavigatedToGame = true;
              router.replace({
                pathname: '/game',
                params: { code, playerId },
              });
            }
          }
          if (Array.isArray(latestPlayers)) {
            setPlayers(latestPlayers);
          }
        }
      } catch {
        // Network errors are intentionally ignored while polling.
      }
    };

    fetchGameState();
    intervalId = setInterval(fetchGameState, 1000);

    return () => {
      isMounted = false;
      clearInterval(intervalId);
    };
  }, [code, hostPlayerId, playerId, selectedDuration]);

  const isHost = playerId === hostPlayerId;
  const challengesGenerated = generatedChallenges.length > 0;

  const exitGame = async () => {
    if (!code || !playerId) {
      return;
    }

    try {
      const response = await fetch(
        `${API_BASE_URL}/games/${encodeURIComponent(code)}/players/${encodeURIComponent(playerId)}`,
        { method: 'DELETE' }
      );
      if (response.ok) {
        router.replace('/');
      } else {
        Alert.alert('Could not exit game', 'You could not be removed from this game.');
      }
    } catch {
      Alert.alert('Could not exit game', 'Please check your connection and try again.');
    }
  };

  const endGame = async () => {
    if (!code || !playerId) {
      return;
    }

    try {
      const response = await fetch(
        `${API_BASE_URL}/games/${encodeURIComponent(code)}/end`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({ playerId }),
        }
      );
      if (response.ok) {
        console.log('GAME STATUS FINISHED — navigating to final scoreboard');
        router.replace({
          pathname: '/results',
          params: { code },
        });
      } else {
        Alert.alert('Could not end game', 'Only the host can end this game.');
      }
    } catch {
      Alert.alert('Could not end game', 'Please check your connection and try again.');
    }
  };

  const prepareChallenges = async () => {
    if (!code || !playerId || preparingChallenges || challengesGenerated) return;
    setPreparingChallenges(true);
    try {
      const response = await fetch(`${API_BASE_URL}/games/${encodeURIComponent(code)}/prepare-challenges`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ playerId }),
      });
      if (!response.ok) {
        const responseBody = await response.text();
        let backendMessage = '';
        try {
          const parsedBody: { message?: unknown } = JSON.parse(responseBody);
          if (typeof parsedBody.message === 'string') {
            backendMessage = parsedBody.message.trim();
          }
        } catch {
          backendMessage = responseBody.trim();
        }
        const errorMessage = backendMessage || 'Please try again.';
        Alert.alert('Could not prepare challenges', errorMessage);
      }
    } catch {
      Alert.alert('Could not prepare challenges', 'Please check your connection and try again.');
    } finally {
      setPreparingChallenges(false);
    }
  };

  const regenerateChallenges = async () => {
    if (!code || !playerId || regenerationCount >= 2) return;
    try {
      const response = await fetch(`${API_BASE_URL}/games/${encodeURIComponent(code)}/regenerate-challenges`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ playerId }),
      });
      if (!response.ok) Alert.alert('Could not regenerate challenges', 'Please try again.');
    } catch {
      Alert.alert('Could not regenerate challenges', 'Please check your connection and try again.');
    }
  };

  const startGame = async (selectedDuration?: string) => {
    if (!code || !playerId) {
      return;
    }

    try {
      const response = await fetch(
        `${API_BASE_URL}/games/${encodeURIComponent(code)}/start`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({ playerId, duration: selectedDuration }),
        }
      );

      if (!response.ok) {
        const errorText = await response.text();
        if (errorText.includes('At least 2 players are required to start the game')) {
          Alert.alert('Need more players', 'At least 2 players are required to start the game.');
        } else {
          Alert.alert('Could not start game', 'Only the host can start this game.');
        }
      }
    } catch {
      Alert.alert('Could not start game', 'Please check your connection and try again.');
    }
  };

  const confirmExit = () => {
    Alert.alert('Exit Game?', 'Are you sure you want to leave this game?', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Exit Game', style: 'destructive', onPress: exitGame },
    ]);
  };

  const confirmEnd = () => {
    Alert.alert('End Game?', 'Are you sure you want to end this game?', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'End Game', style: 'destructive', onPress: endGame },
    ]);
  };

  const confirmStart = (selectedDuration?: string) => {
    Alert.alert(
      'Start Game?',
      'Once the game starts, new players can no longer join.',
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Start Game', onPress: () => startGame(selectedDuration) },
      ]
    );
  };

  const chooseDuration = () => {
    Alert.alert('Choose duration', 'Select the game duration.', [
      ...['10', '20', '30', '45', '60'].map((value) => ({
        text: `Use ${value} minutes`,
        onPress: () => {
          setSelectedDuration(value);
          setDuration(value);
          setRecommendedDuration(null);
        },
      })),
      { text: 'Cancel', style: 'cancel' },
    ]);
  };

  const copyCode = async () => {
    if (!code) {
      return;
    }

    await Clipboard.setStringAsync(code);
    Alert.alert('Code copied!');
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Game Lobby</Text>
      <Text style={styles.codeLabel}>GAME CODE</Text>
      {code ? (
        <>
          <Text style={styles.code}>{code}</Text>
          <Pressable style={styles.copyButton} onPress={copyCode}>
            <Text style={styles.copyButtonText}>Copy Code</Text>
          </Pressable>
          <Text style={styles.subtitle}>Share this code with your friends</Text>
        </>
      ) : (
        <>
          <Text style={styles.subtitle}>No game code received</Text>
          <Pressable style={styles.copyButton} onPress={copyCode} disabled>
            <Text style={styles.copyButtonText}>Copy Code</Text>
          </Pressable>
        </>
      )}
      {playerName ? <Text style={styles.joinedAs}>You joined as: {playerName}</Text> : null}
      <Text style={styles.playersTitle}>Players</Text>
      {players.map((player) => (
        <Text key={player.id} style={styles.player}>
          {player.name}
        </Text>
      ))}
      {status !== 'finished' && playerId && hostPlayerId ? (
        <Pressable style={styles.actionButton} onPress={isHost ? confirmEnd : confirmExit}>
          <Text style={styles.actionButtonText}>{isHost ? 'End' : 'Exit'}</Text>
        </Pressable>
      ) : null}
      {status === 'lobby' && challengePreview.length ? (
        <>
          {challengePreview.map((challenge) => (
            <Text key={challenge.id} style={styles.previewText}>{challenge.challenge}</Text>
          ))}
        </>
      ) : null}
      {isHost && status === 'lobby' ? (
        <>
          {!challengesGenerated ? (
            <Pressable
              style={styles.startButton}
              onPress={prepareChallenges}
              disabled={preparingChallenges}
            >
              <Text style={styles.startButtonText}>Prepare Challenges</Text>
            </Pressable>
          ) : (
            <>
              <Pressable
                style={styles.startButton}
                onPress={regenerateChallenges}
                disabled={regenerationCount >= 2}
              >
                <Text style={styles.startButtonText}>Regenerate ({regenerationCount}/2)</Text>
              </Pressable>
              {selectedDuration ? (
                <>
                  <Text style={styles.previewText}>Duration: {selectedDuration} min</Text>
                  <Pressable style={styles.startButton} onPress={() => confirmStart(selectedDuration)}>
                    <Text style={styles.startButtonText}>Start Game</Text>
                  </Pressable>
                </>
              ) : duration === 'ai' && recommendedDuration ? (
                <>
                  <Text style={styles.previewText}>AI recommends {recommendedDuration} minutes</Text>
                  <Pressable style={styles.startButton} onPress={() => confirmStart(String(recommendedDuration))}>
                    <Text style={styles.startButtonText}>Accept {recommendedDuration} min</Text>
                  </Pressable>
                  <Pressable style={styles.startButton} onPress={chooseDuration}>
                    <Text style={styles.startButtonText}>Choose another duration</Text>
                  </Pressable>
                </>
              ) : (
                <Pressable style={styles.startButton} onPress={() => confirmStart(duration ?? undefined)}>
                  <Text style={styles.startButtonText}>Start Game</Text>
                </Pressable>
              )}
            </>
          )}
        </>
      ) : null}
      {status === 'playing' ? (
        <Text style={styles.startedText}>Game started.</Text>
      ) : challengesGenerated ? (
        <Text style={styles.startedText}>Challenges ready.</Text>
      ) : (
        <Text>Waiting for players...</Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    marginBottom: 12,
  },
  codeLabel: {
    fontSize: 14,
    fontWeight: '700',
    letterSpacing: 1,
    marginTop: 20,
  },
  code: {
    fontSize: 36,
    fontWeight: '800',
    letterSpacing: 5,
    marginVertical: 8,
  },
  copyButton: {
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 8,
    backgroundColor: '#222',
    marginBottom: 12,
  },
  copyButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '700',
  },
  actionButton: {
    position: 'absolute',
    top: 48,
    right: 16,
    minWidth: 56,
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 18,
    backgroundColor: '#333',
  },
  actionButtonText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '700',
  },
  startButton: {
    alignItems: 'center',
    backgroundColor: '#6557E8',
    borderRadius: 12,
    justifyContent: 'center',
    marginTop: 16,
    minHeight: 52,
    paddingHorizontal: 24,
  },
  startButtonText: {
    color: '#fff',
    fontSize: 17,
    fontWeight: '800',
  },
  startedText: {
    fontSize: 16,
    fontWeight: '700',
    marginTop: 16,
  },
  previewText: {
    marginTop: 8,
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 16,
    marginBottom: 20,
  },
  joinedAs: {
    fontSize: 16,
    marginBottom: 20,
  },
  playersTitle: {
    fontSize: 18,
    fontWeight: '700',
    marginBottom: 8,
  },
  player: {
    fontSize: 16,
    marginBottom: 4,
  },
});