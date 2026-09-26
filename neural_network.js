class NeuralNetwork {
    constructor(inputNodes, hiddenNodes, outputNodes) {
        this.inputNodes = inputNodes;
        this.hiddenNodes = hiddenNodes;
        this.outputNodes = outputNodes;

        // Pesos (inicializados aleatoriamente entre -1 y 1)
        this.weightsIH = this.randomMatrix(this.hiddenNodes, this.inputNodes);
        this.weightsHO = this.randomMatrix(this.outputNodes, this.hiddenNodes);

        // Sesgos (biases)
        this.biasH = this.randomMatrix(this.hiddenNodes, 1);
        this.biasO = this.randomMatrix(this.outputNodes, 1);
    }

    // Función de activación: Sigmoide
    sigmoid(x) {
        return 1 / (1 + Math.exp(-x));
    }

    // Procesamiento de datos (Feedforward)
    predict(inputArray) {
        let inputs = this.arrayToMatrix(inputArray);
        
        // Input -> Hidden
        let hidden = this.matrixMultiply(this.weightsIH, inputs);
        hidden = this.matrixAdd(hidden, this.biasH);
        hidden = hidden.map(row => row.map(this.sigmoid));

        // Hidden -> Output
        let output = this.matrixMultiply(this.weightsHO, hidden);
        output = this.matrixAdd(output, this.biasO);
        output = output.map(row => row.map(this.sigmoid));

        return this.matrixToArray(output);
    }

    // --- Utilidades Matemáticas ---
    randomMatrix(rows, cols) {
        return Array.from({ length: rows }, () => 
            Array.from({ length: cols }, () => Math.random() * 2 - 1)
        );
    }

    matrixMultiply(a, b) {
        let result = Array.from({ length: a.length }, () => Array(b[0].length).fill(0));
        for (let i = 0; i < a.length; i++) {
            for (let j = 0; j < b[0].length; j++) {
                for (let k = 0; k < a[0].length; k++) {
                    result[i][j] += a[i][k] * b[k][j];
                }
            }
        }
        return result;
    }

    matrixAdd(a, b) {
        return a.map((row, i) => row.map((val, j) => val + b[i][j]));
    }

    arrayToMatrix(arr) { return arr.map(x => [x]); }
    matrixToArray(mat) { return mat.map(row => row[0]); }
}

// --- TEST DE VERIFICACIÓN ---
const nn = new NeuralNetwork(2, 3, 1); // 2 entradas, 3 ocultas, 1 salida
const output = nn.predict([0.5, 0.8]);
console.log("Salida de la neurona (cerebro inicial):", output);
