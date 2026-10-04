import { View, Text, Pressable, StyleSheet } from 'react-native';
import { router } from 'expo-router';

export default function HomeScreen() {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>AI Game Master</Text>

      <Text style={styles.subtitle}>
        The AI runs the game. You play it.
      </Text>

      <Pressable
        style={styles.button}
        onPress={() => router.push('/create')}
      >
        <Text style={styles.buttonText}>Create Game</Text>
      </Pressable>

      <Pressable
        style={styles.button}
        onPress={() => router.push('/join')}
      >
        <Text style={styles.buttonText}>Join Game</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
    gap: 16,
  },

  title: {
    fontSize: 32,
    fontWeight: 'bold',
  },

  subtitle: {
    fontSize: 16,
    textAlign: 'center',
    marginBottom: 24,
  },

  button: {
    width: '100%',
    maxWidth: 350,
    padding: 16,
    borderRadius: 12,
    backgroundColor: '#111',
    alignItems: 'center',
  },

  buttonText: {
    color: 'white',
    fontSize: 18,
    fontWeight: '600',
  },
});