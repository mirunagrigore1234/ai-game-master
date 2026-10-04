import { useState, type ReactNode } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { router } from 'expo-router';
import { API_BASE_URL } from '@/config/api';

type GameMode = 'mirror' | 'balanced' | 'chaos';

type Environment =
  | 'indoor'
  | 'outdoor'
  | 'restaurant'
  | 'bar'
  | 'street'
  | 'anywhere';

type Difficulty = 'easy' | 'medium' | 'hard';

type DurationOption = 'ai' | 10 | 20 | 30 | 45 | 60;

interface GameState {
  mode: GameMode;
  environment: Environment;
  difficulty: Difficulty | null;
  challengeCount: number;
  duration: DurationOption;
}

const modes: { label: string; value: GameMode }[] = [
  { label: 'Mirror', value: 'mirror' },
  { label: 'Balanced', value: 'balanced' },
  { label: 'Chaos', value: 'chaos' },
];

const environments: { label: string; value: Environment }[] = [
  { label: 'Indoor', value: 'indoor' },
  { label: 'Outdoor', value: 'outdoor' },
  { label: 'Restaurant', value: 'restaurant' },
  { label: 'Bar / Club', value: 'bar' },
  { label: 'Street / Public Space', value: 'street' },
  { label: 'Anywhere', value: 'anywhere' },
];

const difficulties: { label: string; value: Difficulty }[] = [
  { label: 'Easy', value: 'easy' },
  { label: 'Medium', value: 'medium' },
  { label: 'Hard', value: 'hard' },
];

const durations: { label: string; value: DurationOption }[] = [
  { label: 'AI Recommended', value: 'ai' },
  { label: '10 minutes', value: 10 },
  { label: '20 minutes', value: 20 },
  { label: '30 minutes', value: 30 },
  { label: '45 minutes', value: 45 },
  { label: '60 minutes', value: 60 },
];

const initialGameState: GameState = {
  mode: 'mirror',
  environment: 'anywhere',
  difficulty: 'medium',
  challengeCount: 5,
  duration: 'ai',
};

function isValidGameState(game: GameState): boolean {
  return (
    modes.some((option) => option.value === game.mode) &&
    environments.some((option) => option.value === game.environment) &&
    (game.mode === 'chaos'
      ? game.difficulty === null
      : difficulties.some((option) => option.value === game.difficulty)) &&
    Number.isInteger(game.challengeCount) &&
    game.challengeCount >= 1 &&
    game.challengeCount <= 7 &&
    durations.some((option) => option.value === game.duration)
  );
}

export default function CreateGameScreen() {
  const [gameState, setGameState] = useState<GameState>(initialGameState);
  const [hostName, setHostName] = useState('');
  const [isCreating, setIsCreating] = useState(false);

  const selectMode = (mode: GameMode) => {
    setGameState((current) => ({
      ...current,
      mode,
      difficulty:
        mode === 'chaos'
          ? null
          : current.difficulty === null
            ? 'medium'
            : current.difficulty,
    }));
  };

  const createGame = async () => {
    if (isCreating) {
      return;
    }

    if (hostName.trim().length === 0 || hostName.trim().length > 20) {
      Alert.alert('Invalid name', 'Please enter a name between 1 and 20 characters.');
      return;
    }

    if (!isValidGameState(gameState)) {
      Alert.alert('Invalid configuration', 'Please check the game settings and try again.');
      return;
    }

    setIsCreating(true);
    try {
      const response = await fetch(`${API_BASE_URL}/games`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(gameState),
      });

      if (!response.ok) {
        throw new Error(`Backend returned ${response.status}.`);
      }

      const createdGame: { code?: string } = await response.json();
      if (!createdGame.code || createdGame.code.length !== 6) {
        throw new Error('The backend returned an invalid game code.');
      }

      const joinResponse = await fetch(
        `${API_BASE_URL}/games/${encodeURIComponent(createdGame.code)}/join`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({ name: hostName.trim() }),
        }
      );

      if (!joinResponse.ok) {
        throw new Error(`Backend returned ${joinResponse.status}.`);
      }

      const joinedGame: {
        gameCode: string;
        playerId: string;
        playerName: string;
        hostPlayerId: string;
        players: { id: string; name: string }[];
      } = await joinResponse.json();

      router.push({
        pathname: '/lobby',
        params: {
          code: joinedGame.gameCode,
          playerId: joinedGame.playerId,
          playerName: joinedGame.playerName,
          hostPlayerId: joinedGame.hostPlayerId,
          players: JSON.stringify(joinedGame.players),
        },
      });
    } catch {
      Alert.alert(
        'Could not create game',
        'Make sure the backend is running and the phone can reach your computer.'
      );
    } finally {
      setIsCreating(false);
    }
  };

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>Create a game</Text>
        <Text style={styles.subtitle}>Set the rules. Let the AI run the adventure.</Text>
      </View>

      <TextInput
        maxLength={20}
        onChangeText={setHostName}
        placeholder="Your Name"
        style={styles.nameInput}
        value={hostName}
      />

      <OptionSection title="Game mode">
        <View style={styles.optionGrid}>
          {modes.map((option) => (
            <OptionButton
              key={option.value}
              label={option.label}
              selected={gameState.mode === option.value}
              onPress={() => selectMode(option.value)}
            />
          ))}
        </View>
      </OptionSection>

      <OptionSection title="Environment">
        <View style={styles.optionGrid}>
          {environments.map((option) => (
            <OptionButton
              key={option.value}
              label={option.label}
              selected={gameState.environment === option.value}
              onPress={() => setGameState((current) => ({ ...current, environment: option.value }))}
            />
          ))}
        </View>
      </OptionSection>

      {gameState.mode !== 'chaos' && (
        <OptionSection title="Difficulty">
          <View style={styles.optionGrid}>
            {difficulties.map((option) => (
              <OptionButton
                key={option.value}
                label={option.label}
                selected={gameState.difficulty === option.value}
                onPress={() =>
                  setGameState((current) => ({ ...current, difficulty: option.value }))
                }
              />
            ))}
          </View>
        </OptionSection>
      )}

      <OptionSection title="Challenges per player">
        <View style={styles.counter}>
          <Pressable
            accessibilityLabel="Decrease challenges per player"
            disabled={gameState.challengeCount === 1}
            onPress={() =>
              setGameState((current) => ({
                ...current,
                challengeCount: Math.max(1, current.challengeCount - 1),
              }))
            }
            style={({ pressed }) => [
              styles.counterButton,
              pressed && styles.pressed,
              gameState.challengeCount === 1 && styles.disabled,
            ]}
          >
            <Text style={styles.counterButtonText}>−</Text>
          </Pressable>
          <View style={styles.counterValue}>
            <Text style={styles.counterNumber}>{gameState.challengeCount}</Text>
            <Text style={styles.counterLabel}>challenges</Text>
          </View>
          <Pressable
            accessibilityLabel="Increase challenges per player"
            disabled={gameState.challengeCount === 7}
            onPress={() =>
              setGameState((current) => ({
                ...current,
                challengeCount: Math.min(7, current.challengeCount + 1),
              }))
            }
            style={({ pressed }) => [
              styles.counterButton,
              pressed && styles.pressed,
              gameState.challengeCount === 7 && styles.disabled,
            ]}
          >
            <Text style={styles.counterButtonText}>+</Text>
          </Pressable>
        </View>
      </OptionSection>

      <OptionSection title="Duration">
        <View style={styles.optionGrid}>
          {durations.map((option) => (
            <OptionButton
              key={String(option.value)}
              label={option.label}
              selected={gameState.duration === option.value}
              onPress={() => setGameState((current) => ({ ...current, duration: option.value }))}
            />
          ))}
        </View>
      </OptionSection>

      <Pressable
        accessibilityRole="button"
        disabled={isCreating}
        onPress={createGame}
        style={({ pressed }) => [
          styles.createButton,
          pressed && styles.createButtonPressed,
          isCreating && styles.disabled,
        ]}
      >
        <Text style={styles.createButtonText}>{isCreating ? 'Creating Game...' : 'Create Game'}</Text>
      </Pressable>
    </ScrollView>
  );
}

function OptionSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>{title}</Text>
      {children}
    </View>
  );
}

function OptionButton({
  label,
  selected,
  onPress,
}: {
  label: string;
  selected: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="radio"
      accessibilityState={{ selected }}
      onPress={onPress}
      style={({ pressed }) => [
        styles.optionButton,
        selected && styles.optionButtonSelected,
        pressed && styles.pressed,
      ]}
    >
      <Text style={[styles.optionText, selected && styles.optionTextSelected]}>{label}</Text>
    </Pressable>
  );
}

const colors = {
  background: '#10121B',
  card: '#1A1E2B',
  ink: '#FFFFFF',
  muted: '#A9ADBE',
  border: '#30364B',
  accent: '#786BFF',
  accentLight: '#2A2750',
};

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    padding: 24,
    paddingBottom: 40,
    backgroundColor: colors.background,
  },
  header: {
    marginBottom: 24,
  },
  title: {
    color: colors.ink,
    fontSize: 34,
    fontWeight: '800',
    letterSpacing: -0.5,
  },
  subtitle: {
    color: colors.muted,
    fontSize: 15,
    lineHeight: 22,
    marginTop: 8,
  },
  nameInput: {
    backgroundColor: '#151823',
    borderColor: colors.border,
    borderRadius: 12,
    borderWidth: 1,
    color: colors.ink,
    fontSize: 16,
    marginBottom: 24,
    padding: 16,
  },
  section: {
    backgroundColor: colors.card,
    borderColor: colors.border,
    borderRadius: 18,
    borderWidth: 1,
    marginBottom: 24,
    padding: 16,
  },
  sectionTitle: {
    color: colors.ink,
    fontSize: 17,
    fontWeight: '700',
    marginBottom: 12,
  },
  optionGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
  },
  optionButton: {
    alignItems: 'center',
    backgroundColor: colors.card,
    borderColor: colors.border,
    borderRadius: 12,
    borderWidth: 1,
    justifyContent: 'center',
    minHeight: 46,
    paddingHorizontal: 16,
  },
  optionButtonSelected: {
    backgroundColor: colors.accentLight,
    borderColor: colors.accent,
  },
  optionText: {
    color: colors.muted,
    fontSize: 15,
    fontWeight: '600',
  },
  optionTextSelected: {
    color: colors.accent,
    fontWeight: '700',
  },
  counter: {
    alignItems: 'center',
    backgroundColor: colors.card,
    borderColor: colors.border,
    borderRadius: 16,
    borderWidth: 1,
    flexDirection: 'row',
    justifyContent: 'space-between',
    padding: 12,
  },
  counterButton: {
    alignItems: 'center',
    backgroundColor: colors.accentLight,
    borderRadius: 12,
    height: 48,
    justifyContent: 'center',
    width: 48,
  },
  counterButtonText: {
    color: colors.accent,
    fontSize: 28,
    fontWeight: '500',
    lineHeight: 30,
  },
  counterValue: {
    alignItems: 'center',
  },
  counterNumber: {
    color: colors.ink,
    fontSize: 25,
    fontWeight: '800',
  },
  counterLabel: {
    color: colors.muted,
    fontSize: 12,
    marginTop: 2,
  },
  createButton: {
    alignItems: 'center',
    backgroundColor: colors.accent,
    borderRadius: 14,
    justifyContent: 'center',
    marginTop: 4,
    minHeight: 56,
    shadowColor: colors.accent,
    shadowOpacity: 0.3,
    shadowRadius: 10,
    elevation: 4,
  },
  createButtonPressed: {
    opacity: 0.85,
  },
  createButtonText: {
    color: '#FFFFFF',
    fontSize: 17,
    fontWeight: '800',
  },
  pressed: {
    opacity: 0.75,
  },
  disabled: {
    opacity: 0.4,
  },
});