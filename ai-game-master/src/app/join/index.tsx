import { useState } from 'react';
import { router } from 'expo-router';
import { Alert, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { API_BASE_URL } from '@/config/api';

export default function JoinGameScreen() {
  const [code, setCode] = useState('');
  const [name, setName] = useState('');

  const joinGame = async () => {
    const normalizedCode = code.replace(/\s/g, '').toUpperCase();
    const trimmedName = name.trim();

    if (normalizedCode.length !== 6 || trimmedName.length === 0 || trimmedName.length > 20) {
      Alert.alert('Invalid details', 'Please enter a valid name and 6-character game code');
      return;
    }

    try {
      const response = await fetch(
        `${API_BASE_URL}/games/${encodeURIComponent(normalizedCode)}/join`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ name: trimmedName }),
        }
      );

        if (response.status === 404) {
        Alert.alert('Game not found');
        return;
      }

      if (response.status === 400) {
        Alert.alert(
          'Invalid details',
          'Please enter a valid name and 6-character game code'
        );
        return;
      }

      if (response.status === 409) {
         const rawResponse = await response.text();
         const normalizedResponse = rawResponse.toLowerCase();

         if (normalizedResponse.includes('no longer accepting players')) {
          Alert.alert(
            'Game closed',
            'This game is no longer accepting players.'
          );
        } else if (normalizedResponse.includes('already started')) {
          Alert.alert(
            'Game already started',
            'You cannot join this game because the game has already started.'
          );
        } else if (normalizedResponse.includes('name already taken')) {
          Alert.alert(
            'Name already taken',
            'Please choose another name.'
          );
        } else {
          Alert.alert(
            'Cannot join game',
            'The game cannot be joined.'
          );
        }

        return;
      }

      if (!response.ok) {
        Alert.alert('Could not join game');
        return;
      }

      const joinedGame: {
        gameCode: string;
        playerId: string;
        playerName: string;
        hostPlayerId: string;
        players: { id: string; name: string }[];
      } = await response.json();

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
      Alert.alert('Could not join game');
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Join Game</Text>
      <TextInput
        autoCapitalize="characters"
        maxLength={6}
        onChangeText={(value) => setCode(value.replace(/\s/g, '').toUpperCase())}
        placeholder="Game Code"
        style={styles.input}
        value={code}
      />
      <TextInput
        maxLength={20}
        onChangeText={setName}
        placeholder="Your Name"
        style={styles.input}
        value={name}
      />
      <Pressable onPress={joinGame} style={styles.button}>
        <Text style={styles.buttonText}>Join Game</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    padding: 24,
  },
  title: {
    fontSize: 30,
    fontWeight: '800',
    marginBottom: 24,
    textAlign: 'center',
  },
  input: {
    borderColor: '#E1E4EE',
    borderRadius: 12,
    borderWidth: 1,
    fontSize: 16,
    marginBottom: 12,
    padding: 16,
  },
  button: {
    alignItems: 'center',
    backgroundColor: '#6557E8',
    borderRadius: 12,
    marginTop: 8,
    padding: 16,
  },
  buttonText: {
    color: '#FFFFFF',
    fontSize: 17,
    fontWeight: '700',
  },
});