package compilerx.ui;

import java.util.List;

final class Examples {
    record Example(String title, String filename, String source) {}
    static final List<Example> ALL = List.of(
        new Example("Hello world", "main.cpp", """
            #include <iostream>
            using namespace std;

            int main() {
                cout << "Hello, World!" << endl;
                return 0;
            }
            """),
        new Example("Eight operations", "arithmetic.cpp", """
            #include <iostream>
            #include <cmath>
            using namespace std;

            int main() {
                int a = 12, b = 5;

                cout << "Addition:       " << a + b << endl;
                cout << "Subtraction:    " << a - b << endl;
                cout << "Multiplication: " << a * b << endl;
                cout << "Division:       " << a / b << endl;
                cout << "Modulus:        " << a % b << endl;
                cout << "Increment:      " << ++a << endl;
                cout << "Decrement:      " << --b << endl;
                cout << "Exponentiation: " << pow(2, 5) << endl;
                cout << "Precedence:     " << 2 + 3 * 4 << endl;
                cout << "Parentheses:    " << (2 + 3) * 4 << endl;
                cout << "Real division:  " << 12.0 / 5 << endl;

                return 0;
            }
            """),
        new Example("Interactive input", "input.cpp", """
            #include <iostream>
            #include <string>
            using namespace std;

            int main() {
                string name;
                double first, second;

                cout << "What is your name? ";
                cin >> name;
                cout << "Enter two numbers: ";
                cin >> first >> second;

                cout << "Hello, " << name << "!" << endl;
                cout << "Sum: " << first + second << endl;
                cout << "Product: " << first * second << endl;
                return 0;
            }
            """),
        new Example("Conditions", "conditions.cpp", """
            #include <iostream>
            using namespace std;

            int main() {
                int number;
                cout << "Enter an integer: ";
                cin >> number;

                if (number > 0) {
                    cout << "Positive" << endl;
                } else if (number < 0) {
                    cout << "Negative" << endl;
                } else {
                    cout << "Zero" << endl;
                }

                cout << (number % 2 == 0 ? "Even" : "Odd") << endl;
                return 0;
            }
            """),
        new Example("Loops and scope", "loops.cpp", """
            #include <iostream>
            using namespace std;

            int main() {
                int sum = 0;
                for (int i = 1; i <= 10; i++) {
                    sum += i;
                }
                cout << "Sum from 1 to 10: " << sum << endl;

                int count = 3;
                while (count > 0) {
                    cout << count-- << " ";
                }
                cout << "Go!" << endl;
                return 0;
            }
            """),
        new Example("Functions", "functions.cpp", """
            #include <iostream>
            using namespace std;

            long long factorial(int n) {
                if (n <= 1) return 1;
                return n * factorial(n - 1);
            }

            int main() {
                for (int n = 1; n <= 6; n++) {
                    cout << n << "! = " << factorial(n) << endl;
                }
                return 0;
            }
            """),
        new Example("C console I/O", "console.c", """
            #include <stdio.h>
            #include <math.h>

            int main(void) {
                int count;
                double price;

                printf("Enter count and price: ");
                scanf("%d %lf", &count, &price);

                printf("Total: %.2f\\n", count * price);
                printf("2 to the power 8: %.0f\\n", pow(2, 8));
                return 0;
            }
            """)
    );
    private Examples() {}
}
