using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using ObjectFabric;
using System.Windows.Media.Imaging;
using System.IO;
using System.Drawing.Imaging;
using System.Windows.Threading;

namespace SampleImages
{
    /// <summary>
    /// Interaction logic for MainWindow.xaml
    /// </summary>
    public partial class MainWindow : Window
    {
        Resource resource;
        TSet<TArray<double>> positions;

        double x, y;
        bool dragging;

        public MainWindow()
        {
            InitializeComponent();

            Loaded += MainWindow_Loaded;
        }

        async void MainWindow_Loaded(object sender, RoutedEventArgs e)
        {
            Workspace workspace = new Workspace();
            workspace.AddURIHandler(new WebSocketURIHandler());
            resource = workspace.Resolve("ws://localhost:8888/images");

            DispatcherTimer dispatcherTimer = new DispatcherTimer();
            dispatcherTimer.Tick += Refresh;
            dispatcherTimer.Interval = TimeSpan.FromMilliseconds(100);
            dispatcherTimer.Start();

            positions = (TSet<TArray<double>>) await resource.GetAsync();
            button.IsEnabled = true;

            // Register a handler on the set to be notified when an image
            // is added that needs to be displayed.
            positions.CollectionChanged += (_, changeEvent) =>
            {
                AddImageToUI((TArray<double>) changeEvent.NewItems[0]);
            };

            // Some images might already be shared, show them. Use an atomic
            // block to get a stable view of the collection.
            workspace.Atomic(() =>
            {
                foreach (TArray<double> position in positions)
                    AddImageToUI(position);
            });
        }

        private void Refresh(object sender, EventArgs e)
        {
            disconnected.Visibility = Visibility.Hidden;
            ongoing.Visibility = Visibility.Hidden;
            complete.Visibility = Visibility.Hidden;

            switch (((Remote) resource.Origin).Status)
            {
                case Status.DISCONNECTED:
                    disconnected.Visibility = Visibility.Visible;
                    label.Content = "Disconnected";
                    break;
                case Status.CONNECTING:
                    ongoing.Visibility = Visibility.Visible;
                    label.Content = "Connecting...";
                    break;
                case Status.WAITING_RETRY:
                    disconnected.Visibility = Visibility.Visible;
                    label.Content = "Waiting retry...";
                    break;
                case Status.SYNCHRONIZING:
                    ongoing.Visibility = Visibility.Visible;
                    label.Content = "Synchronizing...";
                    break;
                case Status.UP_TO_DATE:
                    complete.Visibility = Visibility.Visible;
                    label.Content = "Up to date";
                    break;
            }
        }

        private void Button_Click_1(object sender, RoutedEventArgs e)
        {
            // Add an image to the set
            TArray<double> position = new TArray<double>(positions.Resource, 2);
            Random rand = new Random();
            position[0] = rand.Next(100) + 50;
            position[1] = rand.Next(100) + 50;
            positions.Add(position);
        }

        private void AddImageToUI(TArray<double> position)
        {
            BitmapImage source = new BitmapImage();

            // Stuff to load image (wtf?)
            using (MemoryStream stream = new MemoryStream())
            {
                Properties.Resources.image.Save(stream, ImageFormat.Png);
                stream.Position = 0;
                source.BeginInit();
                source.CacheOption = BitmapCacheOption.OnLoad;
                source.StreamSource = stream;
                source.EndInit();
                source.Freeze();
            }

            Image image = new Image();
            image.Source = source;
            image.Width = source.PixelWidth;
            image.Height = source.PixelHeight;
            canvas.Children.Add(image);
            Canvas.SetLeft(image, position[0]);
            Canvas.SetTop(image, position[1]);

            // Listen to image info events and move image accordingly

            position.CollectionChanged += (sender, e) =>
            {
                Canvas.SetLeft(image, position[0]);
                Canvas.SetTop(image, position[1]);
            };

            // Listen to image mouse events and update position during drag

            image.MouseDown += delegate(object sender, MouseButtonEventArgs e)
            {
                x = Mouse.GetPosition((IInputElement) sender).X;
                y = Mouse.GetPosition((IInputElement) sender).Y;
                image.CaptureMouse();
                dragging = true;
            };

            image.MouseMove += delegate(object sender, MouseEventArgs e)
            {
                if (dragging)
                {
                    double dx = Mouse.GetPosition((IInputElement) sender).X - x;
                    double dy = Mouse.GetPosition((IInputElement) sender).Y - y;

                    position[0] = Canvas.GetLeft(image) + dx;
                    position[1] = Canvas.GetTop(image) + dy;
                }
            };

            image.MouseUp += delegate(object sender, MouseButtonEventArgs e)
            {
                dragging = false;
                image.ReleaseMouseCapture();
            };
        }
    }
}
